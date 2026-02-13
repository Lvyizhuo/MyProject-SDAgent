#!/usr/bin/env python3
"""
两阶段爬取山东省政策信息：
1. 从省级入口页抓取政策页面/附件 + 地市入口链接
2. 继续抓取各地市入口中的政策页面/附件

默认输出：
- scripts/data/scraped/policies.json
- scripts/data/scraped/provinces.json
"""
import argparse
import json
import os
import re
import time
from collections import deque
from typing import Deque, Dict, List, Optional, Set, Tuple
from urllib.parse import urljoin, urlparse, urldefrag

import requests
from bs4 import BeautifulSoup

try:
    from pdfminer.high_level import extract_text as extract_pdf_text
except Exception:
    extract_pdf_text = None

try:
    import docx
except Exception:
    docx = None


HEADERS = {
    "User-Agent": "Mozilla/5.0 (compatible; PolicyScraper/2.0; +https://example.com)"
}

POLICY_KEYWORDS = [
    "以旧换新",
    "补贴",
    "实施方案",
    "实施细则",
    "通知",
    "公告",
    "政策",
    "申报",
    "消费券",
    "家电",
    "汽车",
    "电动自行车",
    "手机",
    "平板",
    "智能手表",
    "手环",
]

PROVINCES = [
    "北京", "天津", "上海", "重庆", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
    "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南", "湖北", "湖南", "广东",
    "广西", "海南", "四川", "贵州", "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆",
]

SHANDONG_CITIES = [
    "济南市", "青岛市", "淄博市", "枣庄市", "东营市", "烟台市", "潍坊市", "济宁市",
    "泰安市", "威海市", "日照市", "临沂市", "德州市", "聊城市", "滨州市", "菏泽市",
]

FILE_PATTERN = re.compile(r"\.(pdf|docx?|xls|xlsx|zip|rar)$", re.IGNORECASE)


def normalize_url(raw_url: str) -> str:
    clean, _ = urldefrag(raw_url.strip())
    return clean


def is_http_url(url: str) -> bool:
    scheme = urlparse(url).scheme.lower()
    return scheme in ("http", "https")


def is_file_url(url: str) -> bool:
    return bool(FILE_PATTERN.search(url))


def get_host(url: str) -> str:
    return urlparse(url).netloc.lower()


def host_in_scope(url: str, allowed_hosts: Set[str]) -> bool:
    host = get_host(url)
    return any(host == h or host.endswith(f".{h}") for h in allowed_hosts)


def fetch_html(url: str, timeout: int = 20) -> str:
    resp = requests.get(url, headers=HEADERS, timeout=timeout)
    resp.raise_for_status()
    encoding = resp.apparent_encoding or resp.encoding or "utf-8"
    return resp.content.decode(encoding, errors="replace")


def extract_article_text(soup: BeautifulSoup) -> str:
    selectors = [
        "div.TRS_Editor",
        "div.article",
        "div.content",
        "div#content",
        "div.news_content",
        "div#zoom",
        "div.article-content",
        "div.content-main",
    ]
    text_blocks: List[str] = []
    for selector in selectors:
        node = soup.select_one(selector)
        if not node:
            continue
        text_blocks = [n.get_text(strip=True) for n in node.find_all(["p", "div"]) if n.get_text(strip=True)]
        if text_blocks:
            break
    if not text_blocks:
        text_blocks = [n.get_text(strip=True) for n in soup.find_all("p") if n.get_text(strip=True)]
    if not text_blocks and soup.title and soup.title.string:
        return soup.title.string.strip()
    return "\n\n".join(text_blocks).strip()


def extract_links(soup: BeautifulSoup, base_url: str) -> List[Dict[str, str]]:
    links: List[Dict[str, str]] = []
    seen: Set[str] = set()
    for a in soup.find_all("a", href=True):
        href = a["href"].strip()
        if not href or href.startswith("javascript:"):
            continue
        full_url = normalize_url(urljoin(base_url, href))
        if not is_http_url(full_url):
            continue
        if full_url in seen:
            continue
        seen.add(full_url)
        title = a.get_text(strip=True) or full_url
        links.append({"title": title, "url": full_url})
    return links


def is_policy_related(title: str, url: str, content_preview: str = "") -> bool:
    haystack = f"{title} {url} {content_preview}".lower()
    return any(k.lower() in haystack for k in POLICY_KEYWORDS)


def detect_region_anchor(title: str, url: str) -> Optional[Tuple[str, str]]:
    for city in SHANDONG_CITIES:
        if city in title:
            return ("山东", city)
    for province in PROVINCES:
        if province in title:
            return (province, title.strip() or province)

    lower_url = url.lower()
    # 回退规则：山东商务厅的地市栏目常见为 col/col35xxxx
    if "commerce.shandong.gov.cn/col/" in lower_url and "index.html" in lower_url:
        return ("山东", title.strip() or "山东地市入口")
    return None


def uniq_by_url(items: List[Dict]) -> List[Dict]:
    seen: Set[str] = set()
    output: List[Dict] = []
    for item in items:
        url = item.get("url")
        if not url or url in seen:
            continue
        seen.add(url)
        output.append(item)
    return output


def download_file(url: str, dest_dir: str, timeout: int = 30) -> Optional[str]:
    os.makedirs(dest_dir, exist_ok=True)
    filename = os.path.basename(urlparse(url).path) or "download.file"
    local_path = os.path.join(dest_dir, filename)
    try:
        with requests.get(url, headers=HEADERS, stream=True, timeout=timeout) as resp:
            resp.raise_for_status()
            with open(local_path, "wb") as f:
                for chunk in resp.iter_content(chunk_size=8192):
                    if chunk:
                        f.write(chunk)
        return local_path
    except Exception as exc:
        print(f"[WARN] 文件下载失败: {url} -> {exc}")
        return None


def extract_text_from_file(path: str) -> str:
    ext = path.lower()
    if ext.endswith(".pdf") and extract_pdf_text:
        try:
            return extract_pdf_text(path)[:200000]
        except Exception as exc:
            print(f"[WARN] PDF 文本提取失败: {path} -> {exc}")
            return ""
    if ext.endswith(".docx") and docx:
        try:
            document = docx.Document(path)
            return "\n\n".join(p.text for p in document.paragraphs if p.text)
        except Exception as exc:
            print(f"[WARN] DOCX 文本提取失败: {path} -> {exc}")
            return ""
    return ""


def crawl_site(
    start_url: str,
    allowed_hosts: Set[str],
    max_pages: int,
    max_depth: int,
    request_interval: float,
    source_region: str,
) -> Tuple[List[Dict], List[Dict]]:
    queue: Deque[Tuple[str, int, str]] = deque([(normalize_url(start_url), 0, "")])
    visited: Set[str] = set()
    policies: List[Dict] = []
    regions: List[Dict] = []

    while queue and len(visited) < max_pages:
        current_url, depth, parent_url = queue.popleft()
        if current_url in visited:
            continue
        visited.add(current_url)

        try:
            html = fetch_html(current_url)
        except Exception as exc:
            print(f"[WARN] 页面抓取失败: {current_url} -> {exc}")
            continue

        soup = BeautifulSoup(html, "html.parser")
        page_title = soup.title.string.strip() if soup.title and soup.title.string else current_url
        article_text = extract_article_text(soup)
        if is_policy_related(page_title, current_url, article_text[:500]):
            policies.append(
                {
                    "title": page_title,
                    "url": current_url,
                    "type": "article",
                    "source_region": source_region,
                    "source_page": parent_url or current_url,
                    "content": article_text,
                }
            )

        links = extract_links(soup, current_url)
        for link in links:
            link_title = link["title"]
            link_url = link["url"]

            region = detect_region_anchor(link_title, link_url)
            if region:
                province, region_title = region
                regions.append(
                    {
                        "province": province,
                        "title": region_title,
                        "url": link_url,
                        "source_page": current_url,
                    }
                )

            if is_file_url(link_url):
                if is_policy_related(link_title, link_url):
                    policies.append(
                        {
                            "title": link_title,
                            "url": link_url,
                            "type": "file",
                            "source_region": source_region,
                            "source_page": current_url,
                        }
                    )
                continue

            if depth >= max_depth:
                continue
            if not host_in_scope(link_url, allowed_hosts):
                continue

            # 限制只抓取疑似内容页/栏目页，避免跑到无关功能页面
            if any(x in link_url for x in ("/art/", "/col/", "index.html", "detail", "news")):
                queue.append((link_url, depth + 1, current_url))

        if request_interval > 0:
            time.sleep(request_interval)

    return uniq_by_url(policies), uniq_by_url(regions)


def enrich_file_entries(policies: List[Dict], files_dir: str, download: bool) -> List[Dict]:
    output: List[Dict] = []
    for item in policies:
        row = dict(item)
        if row.get("type") == "file" and download:
            local_path = download_file(row["url"], files_dir)
            row["local_path"] = local_path
            if local_path:
                content = extract_text_from_file(local_path)
                if content:
                    row["content"] = content
        output.append(row)
    return output


def save_json(data: List[Dict], output_path: str) -> None:
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_json_if_exists(path: str) -> List[Dict]:
    if not os.path.exists(path):
        return []
    try:
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
            return data if isinstance(data, list) else []
    except Exception as exc:
        print(f"[WARN] 读取历史数据失败，将按空数据处理: {path} -> {exc}")
        return []


def main() -> None:
    parser = argparse.ArgumentParser(description="山东政策两阶段爬虫（省级入口 + 地市入口）")
    parser.add_argument("--seed-url", default="http://czt.shandong.gov.cn/col/col359363/index.html")
    parser.add_argument("--output", default="scripts/data/scraped")
    parser.add_argument("--max-pages", type=int, default=120, help="省级入口最大抓取页面数")
    parser.add_argument("--max-depth", type=int, default=2, help="省级入口最大抓取深度")
    parser.add_argument("--region-max-pages", type=int, default=80, help="每个地市入口最大抓取页面数")
    parser.add_argument("--region-max-depth", type=int, default=2, help="每个地市入口最大抓取深度")
    parser.add_argument("--interval", type=float, default=0.2, help="请求间隔（秒）")
    parser.add_argument("--no-download", action="store_true", help="不下载文件附件")
    parser.add_argument("--full", action="store_true", help="全量模式（不读取历史结果）")
    args = parser.parse_args()

    policies_path = os.path.join(args.output, "policies.json")
    regions_path = os.path.join(args.output, "provinces.json")
    existing_policies = [] if args.full else load_json_if_exists(policies_path)
    existing_regions = [] if args.full else load_json_if_exists(regions_path)
    existing_policy_urls = {x.get("url") for x in existing_policies if x.get("url")}
    existing_region_urls = {x.get("url") for x in existing_regions if x.get("url")}

    seed_url = normalize_url(args.seed_url)
    seed_host = get_host(seed_url)
    allowed_hosts = {seed_host, "commerce.shandong.gov.cn", "www.shandong.gov.cn", "czt.shandong.gov.cn"}

    print(f"[INFO] 第一阶段：抓取省级入口 {seed_url}")
    first_policies, region_entries = crawl_site(
        start_url=seed_url,
        allowed_hosts=allowed_hosts,
        max_pages=args.max_pages,
        max_depth=args.max_depth,
        request_interval=args.interval,
        source_region="山东省级入口",
    )
    first_policies = [x for x in first_policies if x.get("url") not in existing_policy_urls]
    region_entries = [x for x in region_entries if x.get("url") not in existing_region_urls]

    region_entries = uniq_by_url(region_entries)
    print(f"[INFO] 第一阶段完成：政策 {len(first_policies)} 条，地区入口 {len(region_entries)} 条")

    all_policies = list(first_policies)
    crawl_regions = uniq_by_url(existing_regions + region_entries)
    # 第二阶段：爬取每个地市入口
    for idx, region in enumerate(crawl_regions, start=1):
        region_url = region["url"]
        region_title = region.get("title", region_url)
        region_host = get_host(region_url)
        scoped_hosts = {region_host}

        print(f"[INFO] 第二阶段({idx}/{len(crawl_regions)}): {region_title} -> {region_url}")
        region_policies, _ = crawl_site(
            start_url=region_url,
            allowed_hosts=scoped_hosts,
            max_pages=args.region_max_pages,
            max_depth=args.region_max_depth,
            request_interval=args.interval,
            source_region=region_title,
        )
        region_policies = [x for x in region_policies if x.get("url") not in existing_policy_urls]
        all_policies.extend(region_policies)

    all_policies = uniq_by_url(all_policies)
    files_dir = os.path.join(args.output, "files")
    all_policies = enrich_file_entries(all_policies, files_dir, download=not args.no_download)

    merged_policies = uniq_by_url(existing_policies + all_policies)
    merged_regions = uniq_by_url(existing_regions + region_entries)
    save_json(merged_policies, policies_path)
    save_json(merged_regions, regions_path)

    print(f"[DONE] 新增政策: {len(all_policies)} 条，累计政策: {len(merged_policies)} 条 -> {policies_path}")
    print(f"[DONE] 新增地区入口: {len(region_entries)} 条，累计地区入口: {len(merged_regions)} 条 -> {regions_path}")


if __name__ == "__main__":
    main()
