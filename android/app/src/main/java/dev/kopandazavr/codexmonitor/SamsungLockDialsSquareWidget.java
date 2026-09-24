package dev.kopandazavr.codexmonitor;

import dev.kopandazavr.codexmonitor.SamsungLockWidgetSupport;

/* JADX INFO: loaded from: classes.dex */
public final class SamsungLockDialsSquareWidget extends SamsungLockWidgetProvider {
    @Override // dev.kopandazavr.codexmonitor.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Shape shape() {
        return SamsungLockWidgetSupport.Shape.SQUARE;
    }

    @Override // dev.kopandazavr.codexmonitor.SamsungLockWidgetProvider
    protected SamsungLockWidgetSupport.Style style() {
        return SamsungLockWidgetSupport.Style.DIALS;
    }
}
