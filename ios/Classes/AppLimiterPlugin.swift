import Flutter
import UIKit
import DeviceActivity
import FamilyControls
import ManagedSettings
import SwiftUI

var globalMethodCall = ""

public class AppLimiterPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "app_limiter", binaryMessenger: registrar.messenger())
        let instance = AppLimiterPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)

        case "blockApp":
            if #available(iOS 15.0, *) {
                handleAppSelection(result: result) // Trả về nil khi hiển thị UI
            } else {
                result(FlutterError(code: "UNSUPPORTED", message: "iOS 15+ required", details: nil))
            }

        case "requestPermission":
        if #available(iOS 16.0, *) {
            requestPermission(result: result)
        }else {
                result(FlutterError(code: "UNSUPPORTED", message: "iOS 16+ required", details: nil))
            }

        case "unblockApp":
            if #available(iOS 15.0, *) {
                MyModel.shared.unblockAllApps()
                result(nil)
            } else {
                result(FlutterError(code: "UNSUPPORTED", message: "iOS 15+ required", details: nil))
            }

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    @available(iOS 15.0, *)
    private func handleAppSelection(result: @escaping FlutterResult) {
        let status = AuthorizationCenter.shared.authorizationStatus

        if status == .approved {
          MyModel.shared.setShieldRestrictions()
                  result(nil)
        } else {
            Task {
                do {
                    if #available(iOS 16.0, *) {
                        try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                        let newStatus = AuthorizationCenter.shared.authorizationStatus
                        if newStatus == .approved {
                            presentContentView(method: "selectAppsToDiscourage")
                            result(nil)
                        } else {
                            result(FlutterError(code: "PERMISSION_DENIED", message: "User denied permission", details: nil))
                        }
                    } else {
                        result(FlutterError(code: "UNSUPPORTED", message: "iOS 16+ required for authorization", details: nil))
                    }
                } catch {
                    result(FlutterError(code: "AUTH_ERROR", message: "Failed to request authorization", details: error.localizedDescription))
                }
            }
        }
    }

    @available(iOS 16.0, *)
    private func requestPermission(result: @escaping FlutterResult) {
        let status = AuthorizationCenter.shared.authorizationStatus

        if status == .approved {
            result(true)
        } else {
            Task {
                do {
                    try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
                    let newStatus = AuthorizationCenter.shared.authorizationStatus
                    if newStatus == .approved {
                        result(true)
                    } else {
                        result(FlutterError(code: "PERMISSION_DENIED", message: "User denied permission", details: nil))
                    }
                } catch {
                    result(FlutterError(code: "AUTH_ERROR", message: "Failed to request authorization", details: error.localizedDescription))
                }
            }
        }
    }

    private func presentContentView(method: String) {
        if #available(iOS 13.0, *) {
            guard let rootVC = UIApplication.shared.delegate?.window??.rootViewController else {
                return
            }

            globalMethodCall = method
            let vc: UIViewController

            if #available(iOS 15.0, *) {
                // Using SwiftUI in iOS 15+ devices
                vc = UIHostingController(
                    rootView: ContentView()
                        .environmentObject(MyModel.shared)
                        .environmentObject(ManagedSettingsStore())
                )
            } else {
                // Fallback for earlier versions
                vc = UIViewController()
            }
            rootVC.present(vc, animated: true, completion: nil)
        } else {
            print("This feature requires iOS 13 or later")
        }
    }
}