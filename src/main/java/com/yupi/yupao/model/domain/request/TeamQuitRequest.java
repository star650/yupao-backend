package com.yupi.yupao.model.domain.request;

import lombok.Data;
/**
 * 退出队伍 请求体
 */
import java.io.Serializable;

    @Data
    public class TeamQuitRequest implements Serializable {

        private static final long serialVersionUID = -2038884913144640407L;

        /**
          * id
         */
        private Long teamId;

    }