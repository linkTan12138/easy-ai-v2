package com.link.easyai.starter.service;

import com.link.easyai.starter.domain.dto.PageForm;
import com.link.easyai.starter.domain.vo.AiChatResponseVo;
import com.link.easyai.starter.domain.vo.TbChatSessionTaskVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * @deprecated Use {@link com.link.easyai.starter.engine.AiChatService} instead.
 * This legacy service will be removed in a future version.
 */
@Deprecated
public interface AiService {

    AiChatResponseVo chat(String message);

    void updateTaskStatus(Long id, Integer status);

    Page<TbChatSessionTaskVo> pageTask(PageForm pageDto);

}
