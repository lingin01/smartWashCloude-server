package com.company.project.dto;

import lombok.Data;


//获取的是清洗器的列表数据
@Data
public class MachinesDTO {

    /** 页码 */
    private Integer page;
    /** 每页条数 */
    private Integer pageSize;
    /** 搜索关键字 */
    private String keyword;

    /** 清洗器编号*/
    private String machineCode;

}
