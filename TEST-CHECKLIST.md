# Mahar OS — ISO banne ke baad test checklist

Jab terminal me `DONE: ... Mahar.iso` likha aaye, to ye steps:

## 1. ISO verify karo
```bash
cd /mnt/build/mahar-os/build
ls -lh live-image-amd64.hybrid.iso   # ~0.9–1.1GB hona chahiye
sha256sum live-image-amd64.hybrid.iso  # note kar lo, baad me kaam aayegi
```

## 2. Pehle VM me test karo (safe tareeqa)
- VirtualBox/VMware: RAM **kam az kam 2GB** (1GB par browser tabs mushkil chalenge), EFI **ON**, ISO ko optical drive me lagao, boot karo.
- "Live" entry select karo. Agar Secure Boot error de to BIOS me Secure Boot **OFF** kar do (ISO hybrid BIOS+UEFI hai, signed nahi).

## 3. Ya USB par flash karo (real hardware)
- **Ventoy** sab se aasaan (ISO copy-paste), ya Balena Etcher, ya Rufus **DD mode** me.
- USB se boot karo (boot menu key: F12/F9/Esc — laptop ke hisaab se).

## 4. Smoke test (5 minute)
- [ ] Autologin hota hai? (user `mahar`, desktop khulna chahiye bina password ke)
- [ ] IceWM dark "mahar" theme laga hai? Taskbar + wallpaper dikh rahe hain?
- [ ] Start menu khulta hai? (`Super` key / start button)
- [ ] **Mahar Browser** khulta hai aur koi page load hota hai? (internet check)
- [ ] **Mahar Player** aur **Mahar Files** launch hote hain?
- [ ] Shutdown / Reboot sahi kaam karta hai?

## 5. Agar build fail ho jaye to
- `df -h` — kam az kam **10GB free** chahiye `/mnt/build` wali disk par.
- Internet drop hua ho to dobara `sudo ./build.sh` — live-build cache se wahi se resume karta hai.
- Naya error aaye to screenshot bhej dena — error ko pehle reproduce karke fix karunga.

---
Tayyar: 2026-10-12 (build ke dauraan) · Mahar.iso, Debian Bookworm, IceWM, autologin mahar/mahar
