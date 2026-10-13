# Mahar OS — ISO milne ke baad test checklist

**Tayyar ISO:** `Mahar.iso` (~1.27 GB) — GitHub Actions cloud build, release `mahar-iso-1`
**Download:** https://github.com/freerewards443-sys/mahar-os/releases (Latest release → `Mahar.iso`)
Is me **Calamares installer** bhi shamil hai + desktop par **"Install Mahar OS"** icon.

## 1. ISO verify karo
```bash
ls -lh Mahar.iso          # ~1.27GB (1,271,332,864 bytes) hona chahiye
sha256sum Mahar.iso       # note kar lo, baad me kaam aayegi
```
Size chhoti ho ya download adhoora lage to dobara download karo — corrupt ISO se boot fail hoga.

## 2. Pehle VM me test karo (safe tareeqa)
- VirtualBox/VMware: RAM **kam az kam 2GB** (1GB par browser tabs mushkil chalenge), EFI **ON**, ISO ko optical drive me lagao, boot karo.
- "Live" entry select karo. Agar Secure Boot error de to BIOS me Secure Boot **OFF** kar do (ISO hybrid BIOS+UEFI hai, signed nahi).

## 3. Ya USB par flash karo (real hardware)
- **Balena Etcher** sab se aasaan (select ISO → select USB → Flash), ya **Ventoy** (ISO copy-paste), ya Rufus **DD mode** me.
- USB se boot karo (boot menu key: F12/F9/Esc — laptop ke hisaab se).

## 4. Smoke test (5 minute)
- [ ] Autologin hota hai? (user `mahar`, desktop khulna chahiye bina password ke)
- [ ] IceWM dark "mahar" theme laga hai? Taskbar + wallpaper dikh rahe hain?
- [ ] Start menu khulta hai? (`Super` key / start button)
- [ ] Desktop par **"Install Mahar OS"** icon hai? (Calamares — kholo mat, bas icon confirm karo)
- [ ] **Mahar Browser** khulta hai aur koi page load hota hai? (internet check)
- [ ] **Mahar Player** aur **Mahar Files** launch hote hain?
- [ ] Shutdown / Reboot sahi kaam karta hai?

## 5. Screenshot bhejo
Boot ho jaye to desktop ka screenshot bhej dena — main dekh kar confirm karunga sab theek hai. Koi cheez ajeeb lage (theme tooti, app na khule) to uska screenshot + kya kiya tha, ye bata dena.

## 6. Agar boot fail ho jaye to
- Secure Boot OFF karke dobara try karo (sab se common wajah).
- Doosri USB / doosra port try karo; Etcher se dobara flash karo.
- VM me "EFI ON" confirm karo; VirtualBox me "Enable EFI" ticket hona chahiye.
- Phir bhi fail ho to error screen ka photo bhej dena.

---
Tayyar: 2026-10-13 (cloud build ke baad update) · Mahar.iso 1.27GB, Debian Bookworm, IceWM, autologin mahar/mahar, Calamares installer
