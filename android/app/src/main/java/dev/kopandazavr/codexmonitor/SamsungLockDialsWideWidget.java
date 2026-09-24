package dev.kopandazavr.codexmonitor;

import dev.kopandazavr.codexmonitor.SamsungLockWidgetSupport;

/* JADX INFO: loaded from: classes.dex */
public final class SamsungLockDialsWideWidget extends SamsungLockWidgetProvider {
    @Override // dev.kopandazavr.codexmonitor.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.WIDE;
    }

    @Override // dev.kopandazavr.codexmonitor.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.DIALS;
    }
}
