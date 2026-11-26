package cn.edu.scuec.icpc.lab.rule;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('rule:read')")
    public List<RuleService.RuleView> list() {
        return ruleService.list();
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('rule:write')")
    public RuleService.RuleView update(@PathVariable String key,
                                       @RequestBody RuleService.UpdateRequest request) {
        return ruleService.update(key, request.value(), request.mode(),
                request.sourceClause(), request.reason());
    }
}