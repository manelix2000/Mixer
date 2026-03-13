import SwiftUI
import DeckFeature

struct ContentView: View {
    var body: some View {
        DeckView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    ContentView()
}
