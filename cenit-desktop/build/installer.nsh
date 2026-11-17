; Lo incluye electron-builder en el instalador NSIS (build/installer.nsh).
; Desinstalar Cenit quita también "Iniciar con Windows": main.js lo registra con app.setLoginItemSettings en la clave Run
; del usuario con el nombre com.cenit.desktop (el AppUserModelId), y Windows guarda si está activado en StartupApproved.
; En una actualización (el instalador nuevo desinstala el viejo con --updated) se deja como estaba.
; Los datos (%APPDATA%\SkillMap, con desktop-settings.json) se conservan, como antes (deleteAppDataOnUninstall: false).
!macro customUnInstall
  ${ifNot} ${isUpdated}
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Run" "com.cenit.desktop"
    DeleteRegValue HKCU "Software\Microsoft\Windows\CurrentVersion\Explorer\StartupApproved\Run" "com.cenit.desktop"
  ${endIf}
!macroend
