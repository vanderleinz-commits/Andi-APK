import ActivityKit
import SwiftUI
import WidgetKit

@main
struct AndiWidgets: WidgetBundle {
    var body: some Widget {
        AndiWidget()
        AndiFocusActivity()
    }
}

private struct AndiWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "andi.focus.widget", provider: AndiProvider()) { _ in
            VStack(alignment: .leading, spacing: 8) {
                Image(AndiCharacter.classic.imageName).resizable().scaledToFit().frame(width: 44, height: 44)
                    .accessibilityHidden(true)
                Text("Andi").font(.headline)
                Text("Buka Mode Fokus").font(.caption)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .containerBackground(.fill.tertiary, for: .widget)
            .widgetURL(AndiLinks.focus)
        }
        .configurationDisplayName("Andi")
        .description("Buka Mode Fokus dari layar utama.")
        .supportedFamilies([.systemSmall])
    }
}

private struct AndiEntry: TimelineEntry {
    let date: Date
}

private struct AndiProvider: TimelineProvider {
    func placeholder(in context: Context) -> AndiEntry { AndiEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (AndiEntry) -> Void) {
        completion(placeholder(in: context))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<AndiEntry>) -> Void) {
        completion(Timeline(entries: [placeholder(in: context)], policy: .never))
    }
}

private struct AndiFocusActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FocusActivity.self) { context in
            HStack(spacing: 12) {
                face(context).frame(width: 44, height: 44)
                VStack(alignment: .leading) {
                    Text(context.isStale ? "Waktunya istirahat" : "Andi sedang fokus").font(.headline)
                    if !context.isStale { countdown(context) }
                }
                Spacer(minLength: 0)
            }
            .padding(14)
            .activityBackgroundTint(Color(red: 0.08, green: 0.13, blue: 0.23))
            .foregroundStyle(.white)
            .widgetURL(AndiLinks.focus)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    face(context).frame(width: 36, height: 36)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    if !context.isStale { countdown(context).frame(width: 68) }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text(context.isStale ? "Waktunya istirahat" : "Andi sedang fokus").font(.caption)
                }
            } compactLeading: {
                Image(systemName: context.isStale ? "checkmark" : "timer")
            } compactTrailing: {
                if context.isStale { Text("Selesai").font(.caption2) }
                else { countdown(context).frame(width: 52) }
            } minimal: {
                Image(systemName: context.isStale ? "checkmark" : "timer")
            }
            .widgetURL(AndiLinks.focus)
        }
    }

    private func countdown(_ context: ActivityViewContext<FocusActivity>) -> some View {
        Text(timerInterval: context.attributes.startsAt...context.state.endsAt, countsDown: true)
            .monospacedDigit()
    }

    private func face(_ context: ActivityViewContext<FocusActivity>) -> some View {
        let character = AndiCharacter(rawValue: context.state.characterID ?? "") ?? .classic
        return Image(character.imageName).resizable().scaledToFit().accessibilityHidden(true)
    }
}
