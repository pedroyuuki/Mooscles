TalkBack 15 (com.google.android.marvin.talkback) is on the emulator image. Enabled via settings put secure
enabled_accessibility_services (dumpsys: touchExplorationEnabled=true) with the Today field on screen
(TextFieldTalkBackHoldTest, since removed). Tab, DPAD_RIGHT, Alt+DPAD_RIGHT and input swipes (80 ms and 300 ms)
never moved accessibility focus: AccessibilityNodeInfo.isAccessibilityFocused stayed false for 60 s
(talkback-hold-log.txt). No speech could be captured. TalkBack was turned off again afterwards.
