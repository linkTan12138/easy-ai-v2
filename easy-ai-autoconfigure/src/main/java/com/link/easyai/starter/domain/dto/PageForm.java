package com.link.easyai.starter.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @ClassName pageForm
 * @Description 分页参数
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PageForm {

    private Integer currentPage = 1;

    private Integer pageSize = 10;
}
