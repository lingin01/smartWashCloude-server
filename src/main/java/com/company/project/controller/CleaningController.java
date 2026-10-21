package com.company.project.controller;

import com.company.project.domain.Result;
import com.company.project.dto.EmergencyStopDTO;
import com.company.project.dto.StartCleaningDTO;
import com.company.project.dto.SystemResetDTO;
import com.company.project.vo.CleaningResultVO;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 旧操作入口仅保留迁移或不支持说明，不能返回虚假的设备执行成功。
 * 旧页面模拟查询已移除；真实查询使用 /api/cloud/cleaning/boxes/{boxId}。
 */
@RestController
@RequestMapping("/api/cleaning")
public class CleaningController {
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<CleaningResultVO> startCleaning(@RequestBody StartCleaningDTO dto) {
        return new Result<>(405, "自动清洗由现场 PLC 启动，云端不提供远程启动", null);
    }

    @PostMapping("/stop")
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<CleaningResultVO> emergencyStop(@RequestBody EmergencyStopDTO dto) {
        return new Result<>(409, "请使用 /api/cloud/cleaning/boxes/{boxId}/emergency-stop 并提供 requestId", null);
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<CleaningResultVO> systemReset(@RequestBody SystemResetDTO dto) {
        return new Result<>(405, "当前交互范围不提供远程复位或解除急停", null);
    }
}
