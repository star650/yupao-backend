package com.yupi.yupao.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yupi.yupao.model.domain.Tag;
import com.yupi.yupao.mapper.TagMapper;
import com.yupi.yupao.service.TagService;
import org.springframework.stereotype.Service;

/**
* @author 14446
* @description 针对表【tag(标签表)】的数据库操作Service实现
* @createDate 2022-12-07 17:58:20
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService{

}




