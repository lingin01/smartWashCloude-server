package com.company.project.service;

import com.company.project.domain.bo.statistics.StatisticsPeriodBo;
import com.company.project.domain.bo.statistics.StatisticsQueryBo;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** 自然周期解析集中在后端，避免前端或 SQL 手工推算月份天数。 */
@Component
public class StatisticsPeriodResolver {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    public StatisticsPeriodBo resolve(StatisticsQueryBo query) {
        if (query == null || query.periodType() == null) throw new IllegalArgumentException("缺少统计周期");
        LocalDate start;
        LocalDate end;
        switch (query.periodType()) {
            case "DAY" -> {
                start = require(query.date(), "日期");
                end = start.plusDays(1);
            }
            case "MONTH" -> {
                YearMonth month = YearMonth.of(require(query.year(), "年份"), require(query.month(), "月份"));
                start = month.atDay(1);
                end = month.plusMonths(1).atDay(1);
            }
            case "QUARTER" -> {
                int quarter = require(query.quarter(), "季度");
                if (quarter < 1 || quarter > 4) throw new IllegalArgumentException("季度必须为 1～4");
                start = LocalDate.of(require(query.year(), "年份"), (quarter - 1) * 3 + 1, 1);
                end = start.plusMonths(3);
            }
            case "HALF_YEAR" -> {
                int half = require(query.half(), "半年");
                if (half < 1 || half > 2) throw new IllegalArgumentException("半年必须为 1 或 2");
                start = LocalDate.of(require(query.year(), "年份"), half == 1 ? 1 : 7, 1);
                end = start.plusMonths(6);
            }
            case "YEAR" -> {
                start = LocalDate.of(require(query.year(), "年份"), 1, 1);
                end = start.plusYears(1);
            }
            case "CUSTOM" -> {
                if (query.from() == null || query.to() == null || !query.from().isBefore(query.to())) {
                    throw new IllegalArgumentException("自定义时间范围无效");
                }
                return new StatisticsPeriodBo(query.from(), query.to(), "自定义范围");
            }
            default -> throw new IllegalArgumentException("不支持的统计周期: " + query.periodType());
        }
        return new StatisticsPeriodBo(start.atStartOfDay(BUSINESS_ZONE).toInstant(),
                end.atStartOfDay(BUSINESS_ZONE).toInstant(), query.periodType());
    }

    /** 月视图按周一分桶，首尾周裁剪在当月边界以内。 */
    public List<StatisticsPeriodBo> weeksInMonth(YearMonth month) {
        LocalDate cursor = month.atDay(1);
        LocalDate monthEnd = month.plusMonths(1).atDay(1);
        List<StatisticsPeriodBo> buckets = new ArrayList<>();
        int number = 1;
        while (cursor.isBefore(monthEnd)) {
            LocalDate nextMonday = cursor.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
            LocalDate end = nextMonday.isBefore(monthEnd) ? nextMonday : monthEnd;
            buckets.add(new StatisticsPeriodBo(cursor.atStartOfDay(BUSINESS_ZONE).toInstant(),
                    end.atStartOfDay(BUSINESS_ZONE).toInstant(), "第 " + number++ + " 周"));
            cursor = end;
        }
        return buckets;
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException("缺少" + name);
        return value;
    }
}
