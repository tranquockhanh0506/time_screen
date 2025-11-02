import SwiftUI
import FamilyControls

@available(iOS 15.0, *)
struct ContentView: View {
    @EnvironmentObject var model: MyModel
    @Environment(\.presentationMode) var presentationMode

    @ViewBuilder
    func contentView() -> some View {
        switch globalMethodCall {
        case "selectAppsToDiscourage":
            FamilyActivityPicker(selection: $model.selectionToDiscourage)
        default:
            FamilyActivityPicker(selection: $model.selectionToDiscourage)
        }
    }

    var body: some View {
        NavigationView {
            VStack {
                contentView()
            }
            .navigationBarTitle("Select Apps", displayMode: .inline)
            .navigationBarItems(
                leading: Button("Cancel") {
                    presentationMode.wrappedValue.dismiss()
                },
                trailing: Button("Done") {
                    model.setShieldRestrictions()

                    presentationMode.wrappedValue.dismiss()
                }
            )
        }
    }
}

@available(iOS 15.0, *)
struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
            .environmentObject(MyModel())
    }
}