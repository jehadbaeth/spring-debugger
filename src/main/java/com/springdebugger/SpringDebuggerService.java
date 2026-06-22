package com.springdebugger;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.springdebugger.rule.RuleCatalog;

/**
 * Application-level service. Loads the rule catalog once and keeps it for the session.
 * Obtain via SpringDebuggerService.getInstance().
 */
@Service(Service.Level.APP)
public final class SpringDebuggerService {

    private final RuleCatalog catalog;

    public SpringDebuggerService() {
        this.catalog = RuleCatalog.load();
    }

    public static SpringDebuggerService getInstance() {
        return ApplicationManager.getApplication().getService(SpringDebuggerService.class);
    }

    public RuleCatalog getCatalog() {
        return catalog;
    }
}
