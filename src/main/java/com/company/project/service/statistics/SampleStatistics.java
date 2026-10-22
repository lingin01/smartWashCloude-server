package com.company.project.service.statistics;
import com.company.project.domain.bo.TelemetrySample;
import com.company.project.domain.bo.statistics.CleaningStatisticsBo;
import java.math.*;
import java.time.Instant;
import java.util.*;
/** 统计服务私有累计器：保留sum/count/min/max，只累计真实有效测量。 */
final class SampleStatistics {
    private final Map<String,DeviceSamples> devices=new HashMap<>();
    synchronized void accept(TelemetrySample sample) {
        var data=devices.computeIfAbsent(sample.deviceId(),id->new DeviceSamples());
        if (sample.sequence()<=data.sequence) return;
        data.sequence=sample.sequence();data.count++;
        if (data.first==null)data.first=sample.sampledAt();data.last=sample.sampledAt();
        for (var rope:sample.ropes()) if(rope.valid()) add(data,"ropeTension",rope.ropeId(),"kg",rope.tension());
        for (var winch:sample.winches()) {
            if(winch.speedValid())add(data,"winchSpeed",winch.winchId(),"m/min",winch.speed());
            if(winch.heightValid())add(data,"winchHeight",winch.winchId(),"m",winch.heightMeters());
            if(winch.torqueValid())add(data,"winchTorque",winch.winchId(),"kg",winch.torqueKg());
        }
        if(sample.heightValid())add(data,"ringHeight",sample.deviceId(),"m",sample.ringHeightMeters());
    }
    private void add(DeviceSamples d,String metric,String target,String unit,BigDecimal value) {
        if(value==null || target==null || target.isBlank())return;
        d.metrics.computeIfAbsent(metric+"/"+target,key->new Accumulator(metric,target,unit)).add(value);
    }
    synchronized CleaningStatisticsBo snapshot(String deviceId) {
        var d=devices.get(deviceId);
        return d==null?new CleaningStatisticsBo(deviceId,"PROCESS",0,null,null,List.of()):
                new CleaningStatisticsBo(deviceId,"PROCESS",d.count,d.first,d.last,d.metrics.values().stream().map(Accumulator::snapshot).toList());
    }
    private static final class DeviceSamples {
        long sequence=Long.MIN_VALUE,count;Instant first,last;
        final Map<String,Accumulator> metrics=new TreeMap<>();
    }
    private static final class Accumulator {
        final String metric,target,unit;long count;BigDecimal sum=BigDecimal.ZERO,min,max;
        Accumulator(String metric,String target,String unit){this.metric=metric;this.target=target;this.unit=unit;}
        void add(BigDecimal value){sum=sum.add(value);count++;min=min==null?value:min.min(value);max=max==null?value:max.max(value);}
        CleaningStatisticsBo.Metric snapshot(){return new CleaningStatisticsBo.Metric(metric,target,unit,count,min,max,sum.divide(BigDecimal.valueOf(count),4,RoundingMode.HALF_UP));}
    }
}
