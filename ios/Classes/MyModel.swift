import Foundation
import FamilyControls
import ManagedSettings

@available(iOS 15.0, *)
private let _MyModel = MyModel()

@available(iOS 15.0, *)
class MyModel: ObservableObject {
    let store = ManagedSettingsStore()

    @Published var selectionToDiscourage: FamilyActivitySelection

    init() {
        selectionToDiscourage = FamilyActivitySelection()
    }

    class var shared: MyModel {
        return _MyModel
    }

    func setShieldRestrictions() {
        let applications = MyModel.shared.selectionToDiscourage

        store.shield.applicationCategories = ShieldSettings.ActivityCategoryPolicy.all()
    }

    /// Unblock all apps by clearing shield restrictions and resetting the discouraged selection.
    @available(iOS 15.0, *)
    func unblockAllApps() {
        DispatchQueue.main.async {
            self.store.shield.applications = nil
            self.store.shield.applicationCategories = nil
            // Reset the selection so UI reflects the cleared restrictions
            self.selectionToDiscourage = FamilyActivitySelection()
        }
    }
}