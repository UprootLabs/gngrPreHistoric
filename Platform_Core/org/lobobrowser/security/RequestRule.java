package org.lobobrowser.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.lobobrowser.ua.UserAgentContext.Request;
import org.lobobrowser.ua.UserAgentContext.RequestKind;

public class RequestRule {

  enum Permission {
    Allow, Deny, Undecided
  }

  final String requestHostPattern;
  final Permission[] permissions = new Permission[RequestKind.values().length];

  public RequestRule(final String requestHostPattern, final Permission[] permissions) {
    this.requestHostPattern = requestHostPattern;

    assert(permissions.length == this.permissions.length);
    IntStream.range(0, permissions.length).forEach(i -> {
      this.permissions[i] = permissions[i];
    });
  }

  public RequestRule(String requestHostPattern, Map<RequestKind, Permission> permissionMap) {
    this.requestHostPattern = requestHostPattern;
    IntStream.range(0, permissions.length).forEach(i -> {
      this.permissions[i] = permissionMap.getOrDefault(RequestKind.forOrdinal(i), Permission.Undecided);
    });
  }

  public boolean isRequestPermitted(final String frameHost, final Request request) {
    final String requestHost = request.url.getHost();
    if (requestHost.matches(requestHostPattern)) {
      return permissions[request.kind.ordinal()] == Permission.Allow;
    }

    return false;
  }

  public Permission getPermission(final String frameHost, final Request request) {
    final String requestHost = request.url.getHost();
    if (requestHost.matches(requestHostPattern)) {
      return permissions[request.kind.ordinal()];
    }

    return Permission.Undecided;
  }

  public static class RequestRuleSet {
    final private List<RequestRule> rules;
    final private String frameHost;

    public RequestRuleSet(final String frameHost, final List<RequestRule> rules) {
      this.rules = rules;
      this.frameHost = frameHost;
    }

    public boolean isRequestPermitted(final Request request) {
      return rules.stream().anyMatch(rule -> rule.isRequestPermitted(frameHost, request));
    }

    public static RequestRuleSet getRuleSet(final String frameHost) {
      final Map<RequestKind, Permission> defaultPermissions = new HashMap<>();
      defaultPermissions.put(RequestKind.CookieRead, Permission.Allow);
      defaultPermissions.put(RequestKind.CSS, Permission.Allow);
      defaultPermissions.put(RequestKind.Image, Permission.Allow);

      final RequestRule defaultRule = new RequestRule(".*", defaultPermissions);
      final List<RequestRule> rules = new ArrayList<>();
      rules.add(defaultRule);
      return new RequestRuleSet(frameHost, rules);
    }
  }
}
