# Build the Android overlay with fork-specific controls without changing the
# pinned ReXGlue checkout. Fail configuration if the upstream insertion points move.
set(_settings_source "${REXSDK_DIR}/src/ui/overlay/simple_settings_overlay.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${_settings_source}")
file(READ "${_settings_source}" _settings)
set(_controls_anchor "    case 1: {  // Controls")
set(_session_anchor "      header(\"Session\");")
set(_back_anchor "      x += label_extent.x + 26.0f * fs;")
set(_exit_label "\"Close Game\");")
foreach(_anchor IN ITEMS _controls_anchor _session_anchor _back_anchor _exit_label)
    string(FIND "${_settings}" "${${_anchor}}" _anchor_index)
    if(_anchor_index EQUAL -1)
        message(FATAL_ERROR "ReXGlue settings overlay changed at ${_anchor}.")
    endif()
endforeach()

string(REPLACE "${_controls_anchor}" [=[
    case 1: {  // Controls
      header("Touch");
      {
        static thread_local bool show_touch_button;
        show_touch_button = skate3::android_menu::ShowsHiddenTouchButton();
        RowSpec row;
        row.label = "Show TOUCH button";
        row.desc = "Keep the TOUCH button visible when the on-screen controller is hidden.";
        row.options = {"Off", "On"};
        row.flag = &show_touch_button;
        row.on_enum_change = [](int value) {
          skate3::android_menu::SetShowHiddenTouchButton(value != 0);
        };
        rows.push_back(std::move(row));
      }
]=] _settings "${_settings}")
string(REPLACE "${_session_anchor}" [=[
      header("Session");
      {
        RowSpec row;
        row.kind = RowSpec::kAction;
        row.label = "Quit app";
        row.desc = "Close Skate 3 Mobile.";
        row.danger = true;
        row.action = skate3::android_menu::ConfirmQuitApp;
        rows.push_back(std::move(row));
      }
]=] _settings "${_settings}")
string(REPLACE "    for (const LegendGlyph& glyph : glyphs) {" [=[
    for (const LegendGlyph& glyph : glyphs) {
      const float chip_left = Snap(x);
]=] _settings "${_settings}")
string(REPLACE "${_back_anchor}" [=[
      if (std::string_view(glyph.label) == "Back" && clicked &&
          mouse_in(chip_left, legend_y, x + label_extent.x, legend_y + chip_h)) {
        NavigateBack();
      }
      x += label_extent.x + 26.0f * fs;
]=] _settings "${_settings}")
string(REPLACE "${_exit_label}" "\"Return to launcher\");" _settings "${_settings}")

set(_settings_output "${CMAKE_CURRENT_BINARY_DIR}/android_simple_settings_overlay.cpp")
file(CONFIGURE OUTPUT "${_settings_output}" CONTENT
    "#include \"${CMAKE_CURRENT_SOURCE_DIR}/src/skate3_android_menu.h\"\n${_settings}" @ONLY)
set_source_files_properties("${_settings_source}" TARGET_DIRECTORY rexui
    PROPERTIES HEADER_FILE_ONLY TRUE)
target_sources(rexui PRIVATE "${_settings_output}")
