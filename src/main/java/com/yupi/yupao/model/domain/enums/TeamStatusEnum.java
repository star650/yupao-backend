package com.yupi.yupao.model.domain.enums;

/**
 * 队伍状态枚举
 */
public enum TeamStatusEnum {
    /**
     * 枚举值
     */
    PUBLIC(0, "公开"),
    PRIVATE(1, "私有"),
    SECRET(2, "加密");

    private int value;

    private String text;

    /**
     * 类加载的时候就启动这个方法，根据传来的数字参数确定队伍状态（公开，私有，加密）
     * @param value
     * @return
     */
    public static TeamStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
//        获取所有枚举值
        TeamStatusEnum[] values = TeamStatusEnum.values();
//        for循环并遍历所有枚举值，
        for (TeamStatusEnum teamStatusEnum : values) {
//            然后把每一个枚举值和传来的参数作比较，如果相同，就直接return这个枚举值
            if (teamStatusEnum.getValue() == value) {
                return teamStatusEnum;
            }
        }
        return null;
    }

    TeamStatusEnum(int value, String text) {
        this.value = value;
        this.text = text;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}