package com.company.project.converter;

import com.company.project.domain.*;
import com.company.project.domain.bo.TankStatus;
import com.company.project.domain.bo.OperationRecord;
import com.company.project.dto.*;
import com.company.project.param.*;
import com.company.project.vo.*;

import java.time.format.DateTimeFormatter;

/**
 * 清洗模块转换器 — 所有层间字段映射集中在此。
 *
 * Controller / Service / DAO 层禁止出现 set 方法做字段搬运，
 * 全部通过调用本类的转换方法完成。
 *
 * 转换方向:
 *   DTO ──→ Param ──→ Domain ──→ Entity (DAO)
 *                      Domain ──→ VO
 *                      Entity  ──→ Domain
 */
public class CleaningConverter {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ════════════════════ DTO → Param ════════════════════

    public static StartCleaningParam toParam(StartCleaningDTO dto) {
        if (dto == null) return null;
        StartCleaningParam param = new StartCleaningParam();
        param.setCleanerCode(dto.getCleanerId());
        param.setPassword(dto.getPassword());
        param.setOperator(dto.getOperator());
        return param;
    }

    public static EmergencyStopParam toParam(EmergencyStopDTO dto) {
        if (dto == null) return null;
        EmergencyStopParam param = new EmergencyStopParam();
        param.setCleanerCode(dto.getCleanerId());
        return param;
    }

    public static SystemResetParam toParam(SystemResetDTO dto) {
        if (dto == null) return null;
        SystemResetParam param = new SystemResetParam();
        param.setCleanerCode(dto.getCleanerId());
        return param;
    }

    // ════════════════════ Param → Domain ════════════════════

    /** 开始清洗 Param → 运行记录 */
    public static OperationRecord toDomain(StartCleaningParam param, String operator) {
        if (param == null) return null;
        OperationRecord record = new OperationRecord();
        // TODO: set fields after Lombok @Data
        // record.setCleanerCode(String.valueOf(param.getCleanerId()));
        // record.setOperationType("START");
        // record.setOperator(operator);
        return record;
    }

    /** 紧急停止 Param → 运行记录 */
    public static OperationRecord toDomain(EmergencyStopParam param) {
        if (param == null) return null;
        OperationRecord record = new OperationRecord();
        // TODO: record.setCleanerCode(...); record.setOperationType("STOP");
        return record;
    }

    /** 系统复位 Param → 运行记录 */
    public static OperationRecord toDomain(SystemResetParam param) {
        if (param == null) return null;
        OperationRecord record = new OperationRecord();
        // TODO: record.setCleanerCode(...); record.setOperationType("RESET");
        return record;
    }

    // ════════════════════ Domain → VO ════════════════════

    public static CleaningResultVO toVO(CleaningResult result) {
        if (result == null) return null;
        CleaningResultVO vo = new CleaningResultVO();
        // TODO: field mapping after Lombok @Data
        // vo.setSuccess(result.getSuccess());
        // vo.setOperationType(result.getOperationType());
        // vo.setRecordId(result.getRecordId());
        // vo.setMessage(result.getMessage());
        // vo.setCompleteTime(result.getCompleteTime() != null ? DTF.format(result.getCompleteTime()) : null);
        return vo;
    }

    public static CleaningStatusVO toVO(CleaningStatus status) {
        if (status == null) return null;
        CleaningStatusVO vo = new CleaningStatusVO();
        // TODO: flatten nested domain objects into VO fields
        // if (status.getDeviceStatus() != null) {
        //     vo.setDeviceOnlineStatus(...);
        //     vo.setDeviceWorkStatus(...);
        // }
        // if (status.getTankStatus() != null) { ... }
        // if (status.getActiveRecord() != null) { ... }
        return vo;
    }

    public static CleanerVO toVO(DeviceStatus deviceStatus) {
        if (deviceStatus == null) return null;
        CleanerVO vo = new CleanerVO();
        // TODO: field mapping
        // vo.setDeviceCode(deviceStatus.getDeviceCode());
        // vo.setDeviceName(deviceStatus.getDeviceName());
        // ...
        return vo;
    }

    public static TankVO toVO(TankStatus tankStatus) {
        if (tankStatus == null) return null;
        TankVO vo = new TankVO();
        // TODO: field mapping
        // vo.setTankCode(tankStatus.getTankCode());
        // vo.setTemperature(tankStatus.getTemperature());
        // ...
        return vo;
    }

    // ════════════════════ Domain → Entity (DAO 方向，后续实现) ════════════════════

    // TODO: OperationRecord → 对应的 Entity（待 DAO 层建表后补充）

    // ════════════════════ Entity → Domain (DAO 返回方向，后续实现) ════════════════════

    // TODO: Entity → 对应的 Domain 对象（待 DAO 层建表后补充）
}
