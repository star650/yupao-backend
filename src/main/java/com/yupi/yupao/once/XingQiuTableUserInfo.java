package com.yupi.yupao.once;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * 建立对象，和表头形成映射关系,星球表格用户信息
 */
@Data
public class XingQiuTableUserInfo {

    /**
     @ExcelProperty("成员编号")中的汉字要和Excel表中列名一致
     */
    @ExcelProperty("成员编号")
    private String plantCode;

    /**
     @ExcelProperty("成员昵称")中的汉字要和Excel表中列名一致
     */
    @ExcelProperty("成员昵称")
    private String username;

}
