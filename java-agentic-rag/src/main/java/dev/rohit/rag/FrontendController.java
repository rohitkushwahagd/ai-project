package dev.rohit.rag;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendController {
    @GetMapping("/") public String home() { return "redirect:/ui/index.html"; }
}
