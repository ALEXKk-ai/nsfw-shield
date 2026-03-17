import qrcode
import os

# Beam Code for a "Strict" test profile
# V2|Name|Sensitivity|Threshold|Sexual|Violence|Hate|Drugs|Gambling|Harm|Delay|Domain1,Domain2...
beam_code = "V2|Strict Test Profile|STRICT|0.70|1|1|1|1|1|1|1|blocked.com,another.com"

# Generate QR code
qr = qrcode.QRCode(
    version=1,
    error_correction=qrcode.constants.ERROR_CORRECT_L,
    box_size=10,
    border=4,
)
qr.add_data(beam_code)
qr.make(fit=True)

img = qr.make_image(fill_color="black", back_color="white")

# Save to artifacts directory
save_path = r"C:\Users\Administrator\.gemini\antigravity\brain\a0463f45-1cf6-43d4-af35-a93ea9a7eded\test_qr_code.png"
img.save(save_path)

print(f"QR code generated and saved to {save_path}")
