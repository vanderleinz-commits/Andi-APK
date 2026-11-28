import ActivityKit
import Foundation

struct FocusActivity: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        let endsAt: Date
        let characterID: String?
    }

    let startsAt: Date
}

enum AndiLinks {
    static let focus = URL(string: "andi://focus")!
}
