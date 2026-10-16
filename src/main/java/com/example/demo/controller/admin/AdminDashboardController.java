package com.example.demo.controller.admin;

import com.example.demo.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Trang Tổng quan của admin: thẻ số liệu + biểu đồ Chart.js + danh sách bán chạy/sắp hết hàng.
 * Mọi số liệu do DashboardService tính từ CSDL, không nhận từ client.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardService.load());
        model.addAttribute("adminMenu", "dashboard");
        return "admin/dashboard";
    }
}
