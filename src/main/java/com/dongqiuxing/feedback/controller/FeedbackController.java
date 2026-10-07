package com.dongqiuxing.feedback.controller;

import com.dongqiuxing.feedback.entity.Feedback;
import com.dongqiuxing.feedback.service.FeedbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 意见反馈控制器
 *
 * <p>页面跳转与表单提交。表单提交成功后采用 Post/Redirect/Get 模式重定向到列表页，
 * 避免用户刷新时重复提交。</p>
 */
@Slf4j
@Controller
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    /** 首页导航 */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /** 意见提交页面 */
    @GetMapping("/submit")
    public String submitPage(Model model) {
        // 表单用 th:object 绑定，这里必须放一个空对象进 Model，否则页面渲染会报错
        model.addAttribute("feedback", new Feedback());
        log.info("进入意见提交页面");
        return "submit";
    }

    /** 处理意见提交表单 */
    @PostMapping("/submit")
    public String submitFeedback(@ModelAttribute Feedback feedback) {
        log.info("收到意见提交请求，提交人={}", feedback.getUsername());
        feedbackService.submitFeedback(feedback);
        return "redirect:/list";
    }

    /** 意见列表页面 */
    @GetMapping("/list")
    public String listPage(Model model) {
        model.addAttribute("feedbacks", feedbackService.getAllFeedbacks());
        log.info("进入意见列表页面");
        return "list";
    }
}
