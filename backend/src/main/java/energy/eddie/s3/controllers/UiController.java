package energy.eddie.s3.controllers;

import energy.eddie.s3.config.KeycloakConfig;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Controller
@AllArgsConstructor
@EnableConfigurationProperties(KeycloakConfig.class)
public class UiController {

    private static final String UI_INDEX_VIEW = "index";

    private final KeycloakConfig keycloakConfig;

    @GetMapping({"/", "/{a:[^.]*}", "/{a:[^.]*}/{b:[^.]*}"})
    public String index(
            Model model,
            @PathVariable(required = false) @Nullable String a,
            @PathVariable(required = false) @Nullable String b) {
        model.addAttribute("publicUrl", publicUrl());
        model.addAttribute("keycloakHost", keycloakConfig.host());
        model.addAttribute("keycloakRealm", keycloakConfig.realm());
        model.addAttribute("keycloakClient", keycloakConfig.client());
        return UI_INDEX_VIEW;
    }

    private static String publicUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .build()
                .toUriString();
    }
}
