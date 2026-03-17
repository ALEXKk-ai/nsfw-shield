import json
import hashlib
import hmac
import requests
import time
import os

# Configuration
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
HMAC_SECRET = "nsfw-shield-blocklist-signing-key-2026"
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "blocklist.json")
VERSION_FILE = os.path.join(SCRIPT_DIR, "version.txt")
HISTORY_FILE = os.path.join(SCRIPT_DIR, "history.log")
MAX_DOMAINS_PER_CATEGORY = 30

# Trusted sources (example URLs - you can add more)
SOURCES = {
    "adult": [
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts",
    ],
    "gambling": [
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling/hosts",
    ],
    "drugs": [],
    "gore": [],
    "self_harm": [],
    "hate_speech": []
}

def fetch_domains(url):
    """Simple parser for host-style files."""
    try:
        print(f"Fetching {url}...")
        response = requests.get(url, timeout=15)
        response.raise_for_status()
        
        domains = set()
        for line in response.text.splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            
            # Typically "0.0.0.0 domain.com" or "127.0.0.1 domain.com"
            parts = line.split()
            if len(parts) >= 2:
                domain = parts[1].lower()
                # Clean up known non-domain entries
                if domain not in ["localhost", "broadcasthost"]:
                    domains.add(domain)
        return list(domains)
    except Exception as e:
        print(f"Error fetching {url}: {e}")
        return []

def generate():
    # 1. Load current version and old domains for comparison
    version = 1
    old_domains = set()
    if os.path.exists(OUTPUT_FILE):
        with open(OUTPUT_FILE, "r") as f:
            try:
                old_data = json.load(f)
                version = old_data.get("version", 0) + 1
                # Collect all old domains across all categories
                for cat in old_data.get("domains", {}).values():
                    old_domains.update(cat)
            except: pass
    
    if os.path.exists(VERSION_FILE):
        with open(VERSION_FILE, "r") as f:
            try:
                version = int(f.read().strip()) + 1
            except: pass

    # 2. Fetch and categorize domains
    data = {
        "adult": [],
        "gambling": [],
        "drugs": [],
        "gore": [],
        "self_harm": [],
        "hate_speech": []
    }

    for category, urls in SOURCES.items():
        all_for_cat = set()
        for url in urls:
            all_for_cat.update(fetch_domains(url))
        
        # Limit to 30 domains per category to avoid spammy updates
        # We sort them to keep the file stable, but take only the first 30
        list_for_cat = sorted(list(all_for_cat))
        data[category] = list_for_cat[:MAX_DOMAINS_PER_CATEGORY]

    # 3. Create the domains object string for signing
    # We must match the app's JSONObject.toString() behavior as much as possible, 
    # but the app parses it back from a string, so we just need a stable JSON string.
    domains_json_str = json.dumps(data, separators=(',', ':'))

    # 4. Generate HMAC Signature
    signature = hmac.new(
        HMAC_SECRET.encode(),
        domains_json_str.encode(),
        hashlib.sha256
    ).hexdigest()

    # 5. Build final object
    final_output = {
        "version": version,
        "signature": signature,
        "domains": data
    }

    # 6. Save files
    with open(OUTPUT_FILE, "w") as f:
        json.dump(final_output, f, indent=2)
    
    with open(VERSION_FILE, "w") as f:
        f.write(str(version))

    # 7. Update history log with new additions
    all_new_domains = []
    for cat_list in data.values():
        for domain in cat_list:
            if domain not in old_domains:
                all_new_domains.append(domain)
    
    if all_new_domains:
        timestamp = time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime())
        log_entry = f"[{timestamp}] v{version}: Added {len(all_new_domains)} domains: {', '.join(all_new_domains)}\n"
        with open(HISTORY_FILE, "a") as f:
            f.write(log_entry)
        print(f"Logged {len(all_new_domains)} new domains to {HISTORY_FILE}")

    print(f"Successfully generated {OUTPUT_FILE} (v{version}) with {sum(len(v) for v in data.values())} domains.")

if __name__ == "__main__":
    generate()
