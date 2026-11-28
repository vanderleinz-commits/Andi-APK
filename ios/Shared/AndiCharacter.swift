import SwiftUI

enum AndiCharacter: String, CaseIterable, Identifiable {
    case classic, momo, piko, lumi

    var id: String { rawValue }

    var name: String {
        switch self {
        case .classic: "Andi Klasik"
        case .momo: "Momo"
        case .piko: "Piko"
        case .lumi: "Lumi"
        }
    }

    var imageName: String {
        switch self {
        case .classic: "AndiClassic"
        case .momo: "AndiMomo"
        case .piko: "AndiPiko"
        case .lumi: "AndiLumi"
        }
    }

    var tileColor: Color {
        switch self {
        case .classic: Color(red: 0.68, green: 0.76, blue: 0.87)
        case .momo: Color(red: 1.00, green: 0.86, blue: 0.77)
        case .piko: Color(red: 0.78, green: 0.88, blue: 0.97)
        case .lumi: Color(red: 1.00, green: 0.91, blue: 0.69)
        }
    }
}
