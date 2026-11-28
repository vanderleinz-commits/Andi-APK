import ActivityKit
import SwiftUI
import UserNotifications

@main
struct AndiApp: App {
    var body: some Scene {
        WindowGroup { FocusScreen() }
    }
}

@MainActor
private struct FocusScreen: View {
    @AppStorage("andi.focus.ends") private var storedEnd: Double = 0
    @AppStorage("andi.character") private var characterID = AndiCharacter.classic.rawValue
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var ending = false
    @State private var note: String?
    private let completionID = "andi.focus.complete"
    private let navy = Color(red: 0.08, green: 0.13, blue: 0.22)
    private let cream = Color(red: 0.96, green: 0.93, blue: 0.88)
    private let mint = Color(red: 0.52, green: 0.87, blue: 0.78)

    private var character: AndiCharacter { AndiCharacter(rawValue: characterID) ?? .classic }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                Text("andi.").font(.system(.largeTitle, design: .rounded, weight: .black))
                    .accessibilityAddTraits(.isHeader)
                hero
                TimelineView(.periodic(from: .now, by: 1)) { timeline in
                    focusCard(at: timeline.date)
                }
                VStack(alignment: .leading, spacing: 12) {
                    Text("Karakter Andi").font(.title2.bold()).accessibilityAddTraits(.isHeader)
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                        ForEach(AndiCharacter.allCases) { choice in
                            characterTile(choice)
                        }
                    }
                }
            }
            .padding(22)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .background(cream.ignoresSafeArea())
        .foregroundStyle(navy)
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { _, phase in
            if phase == .active && storedEnd > 0 && storedEnd <= Date.now.timeIntervalSince1970 {
                Task { await finishActivities() }
            }
        }
        .onChange(of: characterID) { _, _ in Task { await updateActivityCharacter() } }
    }

    private var hero: some View {
        HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Hai, aku Andi.").font(.title.bold())
                Text(character.name).font(.subheadline.weight(.semibold))
            }
            Spacer(minLength: 0)
            TimelineView(.animation(minimumInterval: 1.0 / 24, paused: reduceMotion || scenePhase != .active)) { frame in
                Image(character.imageName)
                    .resizable().scaledToFit().frame(width: 104, height: 104)
                    .offset(y: reduceMotion ? 0 : sin(frame.date.timeIntervalSinceReferenceDate * 2) * 4)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, 14)
        .frame(minHeight: 124)
        .background(cardSurface(character.tileColor))
    }

    private func focusCard(at date: Date) -> some View {
        let remaining = max(0, Int(ceil(storedEnd - date.timeIntervalSince1970)))
        return VStack(alignment: .leading, spacing: 10) {
            Text("Mode Fokus").font(.title2.bold()).accessibilityAddTraits(.isHeader)
            Text(remaining > 0 ? String(format: "%d:%02d", remaining / 60, remaining % 60)
                 : storedEnd == 0 ? "25:00" : "Waktunya istirahat")
                .font(.title.monospacedDigit())
                .accessibilityIdentifier("focus-timer")
            Button {
                Task {
                    if remaining > 0 { await stop() }
                    else { await start() }
                }
            } label: {
                Text(remaining > 0 ? "Akhiri fokus" : "Mulai fokus 25 menit")
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity, minHeight: 44)
                    .background(cardSurface(mint))
            }
            .buttonStyle(.plain)
            .disabled(ending)
            if let note {
                Text(note).font(.footnote)
            }
        }
        .padding(13)
        .background(cardSurface(.white))
        .onChange(of: remaining) { oldValue, newValue in
            if oldValue > 0 && newValue == 0 { Task { await finishActivities() } }
        }
    }

    private func characterTile(_ choice: AndiCharacter) -> some View {
        let selected = character == choice
        return Button {
            characterID = choice.rawValue
        } label: {
            VStack(spacing: 0) {
                Image(choice.imageName)
                    .resizable().scaledToFit().frame(width: 64, height: 64)
                    .accessibilityHidden(true)
                Text(choice.name).font(.subheadline.weight(.semibold))
            }
            .padding(.bottom, 10)
            .frame(maxWidth: .infinity)
            .background(cardSurface(selected ? mint : choice.tileColor))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
        .accessibilityIdentifier("character-" + choice.id)
    }

    private func cardSurface(_ color: Color) -> some View {
        RoundedRectangle(cornerRadius: 11)
            .fill(color)
            .overlay(RoundedRectangle(cornerRadius: 11).strokeBorder(navy, lineWidth: 2))
            .background(RoundedRectangle(cornerRadius: 11).fill(navy).offset(x: 3, y: 3))
    }

    private func start() async {
        guard !ending, storedEnd <= Date.now.timeIntervalSince1970 else { return }
        ending = true
        defer { ending = false }
        await finishActivities()
        let startTime = Date.now
        let endTime = startTime.addingTimeInterval(25 * 60)
        storedEnd = endTime.timeIntervalSince1970
        note = nil
        if ActivityAuthorizationInfo().areActivitiesEnabled {
            let state = FocusActivity.ContentState(endsAt: endTime, characterID: character.id)
            let content = ActivityContent(state: state, staleDate: endTime)
            do {
                _ = try Activity<FocusActivity>.request(attributes: FocusActivity(startsAt: startTime),
                                                       content: content, pushType: nil)
            } catch {
                note = "Timer berjalan di Andi. Live Activity belum bisa ditampilkan."
            }
        } else {
            note = "Timer berjalan di Andi. Aktifkan Live Activities di Pengaturan iPhone untuk melihatnya di Lock Screen."
        }
        let notifications = UNUserNotificationCenter.current()
        do {
            if try await notifications.requestAuthorization(options: [.alert, .sound]) {
                let message = UNMutableNotificationContent()
                message.title = "Fokus selesai"
                message.body = "Waktunya istirahat."
                message.sound = .default
                let trigger = UNTimeIntervalNotificationTrigger(timeInterval: max(1, endTime.timeIntervalSinceNow),
                                                                repeats: false)
                try await notifications.add(UNNotificationRequest(identifier: completionID,
                                                                   content: message, trigger: trigger))
            } else if note == nil {
                note = "Timer tetap berjalan. Aktifkan notifikasi Andi untuk pengingat selesai."
            }
        } catch {
            if note == nil { note = "Timer tetap berjalan. Pengingat selesai belum bisa dijadwalkan." }
        }
    }

    private func stop() async {
        guard !ending else { return }
        ending = true
        defer { ending = false }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [completionID])
        storedEnd = 0
        note = nil
        await finishActivities()
    }

    private func finishActivities() async {
        for activity in Activity<FocusActivity>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    private func updateActivityCharacter() async {
        let selected = character.id
        for activity in Activity<FocusActivity>.activities {
            let endTime = activity.content.state.endsAt
            guard endTime > .now else { continue }
            let state = FocusActivity.ContentState(endsAt: endTime, characterID: selected)
            await activity.update(ActivityContent(state: state, staleDate: endTime))
        }
    }
}
