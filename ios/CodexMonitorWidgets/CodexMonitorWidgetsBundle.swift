import SwiftUI
import WidgetKit

@main
struct CodexMonitorWidgetsBundle: WidgetBundle {
    var body: some Widget {
        CodexMonitorHomeWidget()
        FiveHourAccessoryWidget()
        WeeklyAccessoryWidget()
        DualAllowanceAccessoryWidget()
    }
}

