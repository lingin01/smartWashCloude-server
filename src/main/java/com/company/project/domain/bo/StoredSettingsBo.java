package com.company.project.domain.bo;
/** 平台保存与PLC生效分别表达，初始保存版本由数据库返回0。 */
public record StoredSettingsBo(long savedVersion, CleaningSettingsBo saved, Long appliedVersion,
                               CleaningSettingsBo applied, String state, String message,String requestId,String commandCode) {
    public StoredSettingsBo(long version,CleaningSettingsBo saved,Long appliedVersion,CleaningSettingsBo applied,String state,String message) {
        this(version,saved,appliedVersion,applied,state,message,null,null);
    }
    public StoredSettingsBo withReceipt(CleaningCommandStatusBo receipt) {
        return new StoredSettingsBo(savedVersion,saved,appliedVersion,applied,state,message,receipt.requestId(),receipt.commandId());
    }
}
