package com.mareg74.osccontroller.mapping

enum class ControlId {
    BTN_A, BTN_B, BTN_X, BTN_Y,
    BTN_L1, BTN_R1,
    BTN_L3, BTN_R3,
    BTN_DPAD_UP, BTN_DPAD_DOWN, BTN_DPAD_LEFT, BTN_DPAD_RIGHT,
    BTN_START, BTN_SELECT,
    AXIS_LX, AXIS_LY, AXIS_RX, AXIS_RY,
    /** Analog left trigger (0…1). */
    AXIS_L2,
    /** Analog right trigger (0…1). */
    AXIS_R2,
}
