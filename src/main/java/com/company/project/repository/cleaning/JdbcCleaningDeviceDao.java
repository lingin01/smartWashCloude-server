package com.company.project.repository.cleaning;

import com.company.project.domain.bo.CleaningSystemTopologyBo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 保存一套清洗系统与盒子、4 台绞机、8 个拉力传感器的固定映射。 */
@Repository
public class JdbcCleaningDeviceDao implements CleaningDeviceDao {
    private static final String SELECT_TOPOLOGY =
            "SELECT s.tank_id,s.system_id,s.box_id,s.ring_id,s.ring_own_weight_kg,"
                    + "w.winch_id,r.rope_id,r.tension_sensor_id "
                    + "FROM cleaning_system s "
                    + "LEFT JOIN cleaning_winch w ON w.system_id=s.system_id "
                    + "LEFT JOIN cleaning_rope_sensor r ON r.winch_id=w.winch_id ";
    private final JdbcTemplate jdbc;

    public JdbcCleaningDeviceDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CleaningSystemTopologyBo> findByBoxId(String boxId) {
        return find(SELECT_TOPOLOGY + "WHERE s.box_id=? ORDER BY w.slot_no,r.slot_no", boxId);
    }

    @Override
    public Optional<CleaningSystemTopologyBo> findBySystemId(String cleaningSystemId) {
        return find(SELECT_TOPOLOGY + "WHERE s.system_id=? ORDER BY w.slot_no,r.slot_no", cleaningSystemId);
    }

    private Optional<CleaningSystemTopologyBo> find(String sql, String key) {
        List<TopologyRow> rows = jdbc.query(sql, (result, ignored) -> new TopologyRow(
                result.getString("tank_id"), result.getString("system_id"),
                result.getString("box_id"), result.getString("ring_id"),
                result.getBigDecimal("ring_own_weight_kg"), result.getString("winch_id"),
                result.getString("rope_id"), result.getString("tension_sensor_id")), key);
        if (rows.isEmpty()) return Optional.empty();

        Map<String, List<CleaningSystemTopologyBo.Rope>> ropesByWinch = new LinkedHashMap<>();
        for (TopologyRow row : rows) {
            if (row.winchId() == null) continue;
            List<CleaningSystemTopologyBo.Rope> ropes = ropesByWinch.computeIfAbsent(
                    row.winchId(), ignored -> new ArrayList<>());
            if (row.ropeId() != null) {
                ropes.add(new CleaningSystemTopologyBo.Rope(row.ropeId(), row.sensorId()));
            }
        }
        List<CleaningSystemTopologyBo.Winch> winches = ropesByWinch.entrySet().stream()
                .map(entry -> new CleaningSystemTopologyBo.Winch(entry.getKey(), entry.getValue()))
                .toList();
        TopologyRow first = rows.get(0);
        CleaningSystemTopologyBo topology = new CleaningSystemTopologyBo(first.tankId(),
                first.systemId(), first.boxId(), first.ringId(), first.ringOwnWeightKg(), winches);
        topology.requireCurrentSiteShape();
        return Optional.of(topology);
    }

    @Override
    @Transactional
    public void insertNew(CleaningSystemTopologyBo topology) {
        topology.requireCurrentSiteShape();
        jdbc.update("INSERT INTO cleaning_system "
                        + "(system_id,tank_id,box_id,ring_id,ring_own_weight_kg) VALUES (?,?,?,?,?)",
                topology.cleaningSystemId(), topology.tankId(), topology.boxId(),
                topology.ringId(), topology.ringOwnWeightKilograms());
        for (int winchIndex = 0; winchIndex < topology.winches().size(); winchIndex++) {
            CleaningSystemTopologyBo.Winch winch = topology.winches().get(winchIndex);
            jdbc.update("INSERT INTO cleaning_winch (winch_id,system_id,slot_no) VALUES (?,?,?)",
                    winch.winchId(), topology.cleaningSystemId(), winchIndex + 1);
            for (int ropeIndex = 0; ropeIndex < winch.ropes().size(); ropeIndex++) {
                CleaningSystemTopologyBo.Rope rope = winch.ropes().get(ropeIndex);
                jdbc.update("INSERT INTO cleaning_rope_sensor "
                                + "(rope_id,winch_id,slot_no,tension_sensor_id) VALUES (?,?,?,?)",
                        rope.ropeId(), winch.winchId(), ropeIndex + 1, rope.tensionSensorId());
            }
        }
    }

    private record TopologyRow(String tankId, String systemId, String boxId,
                               String ringId, BigDecimal ringOwnWeightKg,
                               String winchId, String ropeId, String sensorId) {
    }
}
