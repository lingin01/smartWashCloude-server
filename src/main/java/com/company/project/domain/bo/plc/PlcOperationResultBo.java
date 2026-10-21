package com.company.project.domain.bo.plc;

/** 校验后的操作写结果。超时可能已写入，部分写入不做自动回滚或重发。 */
public record PlcOperationResultBo(PlcOperationBo operation, boolean success, boolean uncertain,
                                   int confirmedWrites, String code) { }
