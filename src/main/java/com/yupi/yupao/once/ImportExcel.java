package com.yupi.yupao.once;

import com.alibaba.excel.EasyExcel;

import java.util.List;

public class ImportExcel {

    public static void main(String[] args) {
        // 写法1：JDK8+ ,不用额外写一个DemoDataListener
        // since: 3.0.0-beta1
        String fileName = "F:\\项目\\用户中心\\user-center-backend\\src\\main\\resources\\test01.xlsx";
        readByListener(fileName);
        synchronousRead(fileName);
    }

    /**
     * 通过监听器去读
     * @param fileName
     */
    public static void readByListener(String fileName){
        // 这里 需要指定读用哪个class去读，然后读取第一个sheet 文件流会自动关闭
        // 这里每次会读取100条数据 然后返回过来 直接调用使用数据就行
        EasyExcel.read(fileName, XingQiuTableUserInfo.class, new TableListener()).sheet().doRead();

    }


    /**
     * 同步的返回，不推荐使用，如果数据量大会把数据放到内存里面
     * 不使用监听器
     */

    public static void synchronousRead(String fileName) {
        // 这里 也可以不指定class，返回一个list，然后读取第一个sheet 同步读取会自动finish
        List<XingQiuTableUserInfo> listMap = EasyExcel
                .read(fileName)
                .head(XingQiuTableUserInfo.class)
                .sheet().doReadSync();

        for (XingQiuTableUserInfo xingQiuTableUserInfo : listMap){
            System.out.println(xingQiuTableUserInfo);
        }

    }

}
