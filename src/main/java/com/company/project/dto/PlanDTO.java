package com.company.project.dto;

import lombok.Data;

@Data
public class PlanDTO {

    //    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    //    @Min(value = 1, message = "每页条数不能小于1")
    private Integer pageSize = 10;

    /** 搜索关键字 */
    private String keyword;

    /** 状态 */
    private String status;



    /** 清洗器编号 */
    private String cleanerCode;

}
