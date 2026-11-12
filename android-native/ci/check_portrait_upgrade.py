"""Smoke test against the signed APKs on a disposable portrait emulator."""
import re
import sys
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "pe.lasflores.controlinterno.beta"
ACTIVITY = f"{PACKAGE}/pe.lasflores.controlinterno.MainActivity"
TEST_NAME = "Prueba Persistencia"


def adb(*args):
    return subprocess.check_output(["adb", *args], text=True).strip()


def find_text(text, last=False):
    for _ in range(8):
        adb("shell", "uiautomator", "dump", "/sdcard/las-flores-window.xml")
        xml = adb("shell", "cat", "/sdcard/las-flores-window.xml")
        matches = [node for node in ET.fromstring(xml).iter("node") if node.get("text") == text]
        if matches:
            return matches[-1] if last else matches[0]
        time.sleep(1)
    raise RuntimeError(f"No se encontró {text!r} en Android")


def tap(text, last=False):
    node = find_text(text, last)
    left, top, right, bottom = map(int, re.findall(r"\d+", node.attrib["bounds"]))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def open_menu_item(title):
    for _ in range(8):
        adb("shell", "uiautomator", "dump", "/sdcard/las-flores-window.xml")
        xml = adb("shell", "cat", "/sdcard/las-flores-window.xml")
        nodes = list(ET.fromstring(xml).iter("node"))
        menu = next((n for n in nodes if n.get("content-desc") == "Abrir menú de navegación"), None)
        if menu is not None:
            left, top, right, bottom = map(int, re.findall(r"\d+", menu.attrib["bounds"]))
            adb("shell", "input", "tap", str((left+right)//2), str((top+bottom)//2))
            tap(title, last=True)
            return
        time.sleep(1)
    tap(title, last=True)


def open_personal():
    open_menu_item("Personal")


def launch():
    result = adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    if "Status: ok" not in result:
        raise RuntimeError(result)


def install(*args):
    result = adb("install", *args)
    if not result.endswith("Success"):
        raise RuntimeError(result)


def open_updates():
    # Use the same explicit deep link as the app's update notifications.
    result=adb("shell","am","start","-W","-a","pe.lasflores.controlinterno.OPEN_UPDATES","-n",ACTIVITY)
    if "Status: ok" not in result:
        raise RuntimeError(result)
    find_text("Centro de actualizaciones")


def check_update_notification_switch():
    open_updates()
    def switch_node():
        for _ in range(7):
            adb("shell", "uiautomator", "dump", "/sdcard/las-flores-window.xml")
            xml=adb("shell","cat","/sdcard/las-flores-window.xml")
            nodes=list(ET.fromstring(xml).iter("node"))
            if any(n.get("text")=="Avisar de nuevas versiones" for n in nodes):
                switches=[n for n in nodes if n.get("class")=="android.widget.Switch" and n.get("checkable")=="true"]
                if switches:return switches[0]
            adb("shell","input","swipe","360","1220","360","610","400")
        raise RuntimeError("No se encontró el interruptor de avisos")
    def toggle(node):
        left,top,right,bottom=map(int,re.findall(r"\d+",node.attrib["bounds"]))
        adb("shell","input","tap",str((left+right)//2),str((top+bottom)//2))
        time.sleep(1)
    node=switch_node()
    if node.get("checked")!="true":toggle(node)
    if switch_node().get("checked")!="true":
        raise RuntimeError("No se activaron los avisos")
    adb("shell","am","force-stop",PACKAGE)
    launch()
    open_personal()
    find_text(TEST_NAME)
    open_updates()
    node=switch_node()
    if node.get("checked")!="true":raise RuntimeError("No se conservó el ajuste de avisos")
    toggle(node)
    if switch_node().get("checked")!="false":raise RuntimeError("No se desactivaron los avisos")
    print("Avisos activados, reinicio sin cierre y desactivación verificados.",flush=True)


def main():
    adb("shell", "wm", "size", "720x1600")
    adb("shell", "wm", "density", "320")
    install("android-native/dist/previous/las-flores-beta.apk")
    launch()
    code = 40025 if "android-beta-v40025" in sys.argv else (40024 if "android-beta-v40024" in sys.argv else (40022 if "android-beta-v40022" in sys.argv else (40021 if "android-beta-v40021" in sys.argv else 40008)))
    if code in (40021, 40022, 40024, 40025):
        open_personal()
    else:
        tap("Personal", last=True)
    tap("+ Agregar trabajador")
    tap("Nombre completo")
    adb("shell", "input", "text", TEST_NAME.replace(" ", "%s"))
    adb("shell", "input", "keyevent", "4")
    tap("Guardar")
    find_text(TEST_NAME)
    print("Registro de prueba creado antes de actualizar", flush=True)

    install("-r", "android-native/dist/las-flores-beta.apk")
    package_info = adb("shell", "dumpsys", "package", PACKAGE)
    if not re.search(rf"\bversionCode={code}\b", package_info):
        raise RuntimeError(f"Android no instaló {code}")
    launch()
    if code in (40021, 40022, 40024, 40025):
        open_personal()
        find_text(TEST_NAME)
        adb("shell", "am", "force-stop", PACKAGE)
        launch()
        open_personal()
        find_text(TEST_NAME)
        print("Registro conservado tras actualizar y reiniciar la app", flush=True)
        if code in (40024, 40025):
            tap("Ver ficha integral", last=True)
            find_text("Ficha integral")
            find_text(TEST_NAME)
            tap("Volver a Personal")
            find_text(TEST_NAME)
            print("Ficha individual y navegación verificadas.", flush=True)
        if code == 40025:
            open_menu_item("Uniformes")
            tap("Configurar requisitos")
            tap("+ Agregar requisito")
            find_text("Nuevo requisito")
            find_text("1. Información del requisito")
            find_text("Crear requisito")
            tap("Cancelar")
            find_text("Requisitos de uniformes")
            print("Editor de requisitos y navegación verificados.", flush=True)
            check_update_notification_switch()
        if code == 40022:
            open_menu_item("Incidentes")
            find_text("Resumen del mes")
            find_text("Casos pendientes")
            tap("+ Registrar incidente")
            find_text("Nuevo incidente")
            find_text("1. ¿Qué ocurrió?")
            tap("Cancelar")
            find_text("Resumen del mes")
            print("Nuevo módulo y formulario de Incidentes verificados.", flush=True)
        return
    find_text("Gestión de Personal")
    tap("Personal", last=True)
    find_text(TEST_NAME)
    tap("Briefings", last=True)
    find_text("Resumen del día · Ambos grupos")
    tap("Uniformes", last=True)
    find_text("Control de uniformes")
    tap("Inicio", last=True)
    find_text("Gestión de Personal")
    print("Actualización instalada sobre 4.0007. Registro y pantallas verificados.", flush=True)


if __name__ == "__main__":
    main()
