#pragma once

#include <rex/main_android.h>

namespace skate3::android_menu {

inline void ConfirmQuitApp() {
  auto* env = rex::GetAndroidThreadJniEnv();
  auto activity = rex::GetAndroidApplicationContext();
  if (!env || !activity) return;
  auto type = env->GetObjectClass(activity);
  auto method = env->GetMethodID(type, "confirmQuitApp", "()V");
  env->DeleteLocalRef(type);
  if (!method) {
    env->ExceptionClear();
    return;
  }
  env->CallVoidMethod(activity, method);
}

inline bool ShowsHiddenTouchButton() {
  auto* env = rex::GetAndroidThreadJniEnv();
  auto activity = rex::GetAndroidApplicationContext();
  if (!env || !activity) return false;
  auto type = env->GetObjectClass(activity);
  auto method = env->GetMethodID(type, "showsHiddenTouchButton", "()Z");
  env->DeleteLocalRef(type);
  if (!method) {
    env->ExceptionClear();
    return false;
  }
  return env->CallBooleanMethod(activity, method) == JNI_TRUE;
}

inline void SetShowHiddenTouchButton(bool show) {
  auto* env = rex::GetAndroidThreadJniEnv();
  auto activity = rex::GetAndroidApplicationContext();
  if (!env || !activity) return;
  auto type = env->GetObjectClass(activity);
  auto method = env->GetMethodID(type, "setShowHiddenTouchButton", "(Z)V");
  env->DeleteLocalRef(type);
  if (!method) {
    env->ExceptionClear();
    return;
  }
  env->CallVoidMethod(activity, method, static_cast<jboolean>(show));
}

}  // namespace skate3::android_menu
