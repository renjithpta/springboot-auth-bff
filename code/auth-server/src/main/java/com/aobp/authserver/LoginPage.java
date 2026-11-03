package com.aobp.authserver;

import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Renders local login and external provider links from the configured registrations. */
@Controller
public class LoginPage {
  private final ObjectProvider<ClientRegistrationRepository> repository;

  public LoginPage(ObjectProvider<ClientRegistrationRepository> repository) {
    this.repository = repository;
  }

  /** Lists provider identifiers; new OIDC registrations require no UI code changes. */
  @GetMapping("/login")
  String login(Model model) {
    var links = new ArrayList<Map<String, String>>();
    if (repository.getIfAvailable() instanceof Iterable<?> registrations)
      for (Object entry : registrations) {
        var c = (ClientRegistration) entry;
        links.add(
            Map.of(
                "name",
                c.getClientName(),
                "url",
                "/oauth2/authorization/" + c.getRegistrationId()));
      }
    model.addAttribute("providers", links);
    return "login";
  }
}
