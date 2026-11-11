# Remote button mappings

TV Detox can remap physical remote shortcut buttons to apps already installed on the TV.

## Open the editor

On the TV, open **Settings → Remote buttons**.

The first time you use key learning, choose **Open Accessibility settings** and enable **TV Detox**.
Android asks you to confirm this because an accessibility service can receive remote key events. This
service filters only mapped keys; it cannot read screen contents and has no network permission.

## Change a button

- **Change app** picks the app that an existing button opens.
- **Learn key** waits for you to press the physical remote shortcut. The mapping saves immediately.
- **Add remote button** lets you choose an app first, then press a remote shortcut to map it.
- **Remove** returns that button to the TV’s normal behavior.

The first run seeds the R646 profile: Netflix, YouTube, TCL TV, and Prime Video. Other TVs can use
**Learn key** to record their own Android key code and scan code. A TV may consume a remote shortcut
in firmware before Android sees it; those buttons cannot be mapped by an app.

Home, Back, menu, navigation, Enter, power, volume, and channel keys are protected from learning.
Mappings are private to the TV and stored in TV Detox app data.

If you previously enabled the standalone **TV Detox Remote Buttons** helper, turn that service off
in Accessibility before enabling TV Detox’s built-in service. Only one service should handle these
shortcut keys at a time.

To undo, turn off **TV Detox** in Android’s Accessibility settings. This disables all remote key
mappings without changing the apps they target.
