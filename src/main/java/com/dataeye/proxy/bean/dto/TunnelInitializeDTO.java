package com.dataeye.proxy.bean.dto;

import lombok.*;

import java.util.List;

/**
 * @author jaret
 * @date 2022/3/30 11:15
 * @description
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TunnelInitializeDTO {

    private String initCount;
    private List<TunnelInstance> tunnelList;

}
