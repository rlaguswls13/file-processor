package com.fileprocessor.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
public class FileTransactionHelper {
    public void registerFileRollback(Runnable rollbackAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                        log.warn("Database transaction rolled back. Triggering registered file rollback action.");
                        try {
                            rollbackAction.run();
                        } catch (Exception e) {
                            log.error("Failed to execute file rollback action", e);
                        }
                    }
                }
            });
            log.debug("Successfully registered file rollback action with Spring transaction synchronization.");
        } else {
            log.warn("Spring transaction synchronization is not active. Rollback action will not be automatically triggered.");
        }
    }
}
