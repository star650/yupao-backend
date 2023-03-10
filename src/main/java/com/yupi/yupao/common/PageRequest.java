package com.yupi.yupao.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页请求参数
 */
@Data
public class PageRequest implements Serializable {

    private static final long serialVersionUID = -3183347429247458436L;
    /**
     * 页面大小
     */
    protected int pageSize = 10;
    /**
     * 当前第几页
     */
    protected int pageNum = 1;




}
