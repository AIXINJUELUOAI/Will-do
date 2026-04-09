import argparse
import copy
import re
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory
import xml.etree.ElementTree as ET


W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
XML_NS = "http://www.w3.org/XML/1998/namespace"
NS = {"w": W_NS}


def w_tag(name: str) -> str:
    return f"{{{W_NS}}}{name}"


def normalize_text(text: str) -> str:
    return re.sub(r"\s+", "", text or "").strip().lower()


def is_chapter_heading_line(text: str) -> bool:
    stripped = (text or "").strip()
    return bool(re.match(r"^第[0-9一二三四五六七八九十]+章\s*[^，。,；;：:]{1,30}$", stripped))


def paragraph_text(p: ET.Element) -> str:
    return "".join((t.text or "") for t in p.findall(".//w:t", NS))


def paragraph_style(p: ET.Element) -> str:
    ppr = p.find("w:pPr", NS)
    if ppr is None:
        return ""
    pstyle = ppr.find("w:pStyle", NS)
    if pstyle is None:
        return ""
    return pstyle.attrib.get(w_tag("val"), "")


def is_paragraph(el: ET.Element) -> bool:
    return el.tag == w_tag("p")


def iter_body_elements(root: ET.Element):
    body = root.find("w:body", NS)
    if body is None:
        raise ValueError("document.xml does not contain w:body")
    return body, list(body)


def find_first_index(elements, predicate):
    for i, el in enumerate(elements):
        if predicate(el):
            return i
    return None


def find_first_index_from(elements, start, predicate):
    for i in range(start, len(elements)):
        if predicate(elements[i]):
            return i
    return None


def is_heading_text(text: str, kind: str) -> bool:
    norm = normalize_text(text)
    if kind == "cn_abstract":
        return norm in {"摘要", "摘要"}
    if kind == "en_abstract":
        return norm == "abstract"
    if kind == "toc":
        return norm == "目录"
    if kind == "refs":
        return norm == "参考文献"
    if kind == "ack":
        return norm == "致谢"
    if kind == "chapter1":
        return is_chapter_heading_line(text)
    return False


def classify_paragraph(text: str) -> str:
    stripped = text.strip()
    norm = normalize_text(text)
    if not stripped:
        return "blank"
    if norm == "摘要":
        return "abstract_heading"
    if norm == "abstract":
        return "en_abstract_heading"
    if norm == "参考文献":
        return "refs_heading"
    if norm == "致谢":
        return "ack_heading"
    if is_chapter_heading_line(stripped):
        return "chapter"
    if re.match(r"^\d+\.\d+\.\d+\.\d+", stripped):
        return "heading4"
    if re.match(r"^\d+\.\d+\.\d+", stripped):
        return "heading3"
    if re.match(r"^\d+\.\d+", stripped):
        return "heading2"
    return "body"


def normalize_paragraph_texts(texts):
    normalized = []
    prev_blank = True
    for text in texts:
        kind = classify_paragraph(text)
        if kind == "blank":
            if prev_blank:
                continue
            normalized.append("")
            prev_blank = True
            continue
        if kind in {
            "abstract_heading",
            "en_abstract_heading",
            "refs_heading",
            "ack_heading",
            "chapter",
            "heading2",
            "heading3",
            "heading4",
        }:
            while normalized and normalized[-1] == "":
                normalized.pop()
            normalized.append(text.strip())
            prev_blank = False
            continue
        normalized.append(text)
        prev_blank = False

    while normalized and normalized[-1] == "":
        normalized.pop()
    return normalized


def sample_from_template(elements, kind: str) -> ET.Element:
    patterns = {
        "abstract_heading": lambda t: is_heading_text(t, "cn_abstract"),
        "en_abstract_heading": lambda t: is_heading_text(t, "en_abstract"),
        "chapter": lambda t: is_chapter_heading_line(t),
        "heading2": lambda t: bool(re.match(r"^\d+\.\d+(?!\.)", t.strip())),
        "heading3": lambda t: bool(re.match(r"^\d+\.\d+\.\d+(?!\.)", t.strip())),
        "heading4": lambda t: bool(re.match(r"^\d+\.\d+\.\d+\.\d+", t.strip())),
        "refs_heading": lambda t: is_heading_text(t, "refs"),
        "ack_heading": lambda t: is_heading_text(t, "ack"),
        "body": lambda t: len(t.strip()) > 20,
    }
    matcher = patterns[kind]
    for el in elements:
        if is_paragraph(el) and matcher(paragraph_text(el)):
            return el
    if kind == "heading4":
        return sample_from_template(elements, "heading3")
    raise ValueError(f"Could not find template sample for {kind}")


def first_run_rpr(p: ET.Element):
    for r in p.findall("w:r", NS):
        rpr = r.find("w:rPr", NS)
        if rpr is not None:
            return copy.deepcopy(rpr)
    return None


def paragraph_with_text(text: str, sample_p: ET.Element) -> ET.Element:
    new_p = ET.Element(w_tag("p"))
    ppr = sample_p.find("w:pPr", NS)
    if ppr is not None:
        new_p.append(copy.deepcopy(ppr))

    if not text:
        return new_p

    new_r = ET.Element(w_tag("r"))
    rpr = first_run_rpr(sample_p)
    if rpr is not None:
        new_r.append(rpr)

    t = ET.Element(w_tag("t"))
    if text[:1].isspace() or text[-1:].isspace():
        t.set(f"{{{XML_NS}}}space", "preserve")
    t.text = text
    new_r.append(t)
    new_p.append(new_r)
    return new_p


def extract_text_paragraphs(doc_path: Path):
    with zipfile.ZipFile(doc_path, "r") as zf:
        root = ET.fromstring(zf.read("word/document.xml"))
    _, elements = iter_body_elements(root)
    return [paragraph_text(el) for el in elements if is_paragraph(el)]


def locate_source_ranges(paragraphs):
    idx_cn = idx_en = idx_toc = idx_ch1 = idx_refs = idx_ack = None
    for i, text in enumerate(paragraphs):
        if idx_cn is None and is_heading_text(text, "cn_abstract"):
            idx_cn = i
        elif idx_en is None and is_heading_text(text, "en_abstract"):
            idx_en = i
        elif idx_toc is None and is_heading_text(text, "toc"):
            idx_toc = i
        elif idx_ch1 is None and is_heading_text(text, "chapter1"):
            idx_ch1 = i
        elif idx_refs is None and is_heading_text(text, "refs"):
            idx_refs = i
        elif idx_ack is None and is_heading_text(text, "ack"):
            idx_ack = i

    needed = {
        "摘要": idx_cn,
        "Abstract": idx_en,
        "目录": idx_toc,
        "第1章": idx_ch1,
        "参考文献": idx_refs,
        "致谢": idx_ack,
    }
    missing = [name for name, idx in needed.items() if idx is None]
    if missing:
        raise ValueError(f"Source document missing markers: {', '.join(missing)}")

    return {
        "cn_abstract": normalize_paragraph_texts(paragraphs[idx_cn:idx_en]),
        "en_abstract": normalize_paragraph_texts(paragraphs[idx_en:idx_toc]),
        "main": normalize_paragraph_texts(paragraphs[idx_ch1:idx_refs]),
        "refs": normalize_paragraph_texts(paragraphs[idx_refs:idx_ack]),
        "ack": normalize_paragraph_texts(paragraphs[idx_ack:]),
    }


def build_rendered_paragraphs(texts, samples):
    rendered = []
    for text in texts:
        kind = classify_paragraph(text)
        if kind == "blank":
            rendered.append(paragraph_with_text("", samples["body"]))
            continue
        if kind == "chapter":
            sample_key = "chapter"
        elif kind == "heading2":
            sample_key = "heading2"
        elif kind == "heading3":
            sample_key = "heading3"
        elif kind == "heading4":
            sample_key = "heading4"
        elif kind == "abstract_heading":
            sample_key = "abstract_heading"
        elif kind == "en_abstract_heading":
            sample_key = "en_abstract_heading"
        elif kind == "refs_heading":
            sample_key = "refs_heading"
        elif kind == "ack_heading":
            sample_key = "ack_heading"
        else:
            sample_key = "body"
        rendered.append(paragraph_with_text(text, samples[sample_key]))
    return rendered


def update_document_xml(template_doc: Path, source_doc: Path, output_doc: Path):
    source_paragraphs = extract_text_paragraphs(source_doc)
    ranges = locate_source_ranges(source_paragraphs)

    with TemporaryDirectory() as tmpdir:
        tmpdir = Path(tmpdir)
        with zipfile.ZipFile(template_doc, "r") as zf:
            zf.extractall(tmpdir)

        document_xml = tmpdir / "word" / "document.xml"
        root = ET.parse(document_xml).getroot()
        body, elements = iter_body_elements(root)

        idx_cn = find_first_index(elements, lambda el: is_paragraph(el) and is_heading_text(paragraph_text(el), "cn_abstract"))
        idx_toc = find_first_index(elements, lambda el: is_paragraph(el) and is_heading_text(paragraph_text(el), "toc"))
        idx_ch1 = find_first_index_from(
            elements,
            (idx_toc + 1) if idx_toc is not None else 0,
            lambda el: is_paragraph(el)
            and is_heading_text(paragraph_text(el), "chapter1")
            and not paragraph_style(el).startswith("TOC"),
        )
        idx_refs = find_first_index_from(
            elements,
            (idx_ch1 + 1) if idx_ch1 is not None else 0,
            lambda el: is_paragraph(el)
            and is_heading_text(paragraph_text(el), "refs")
            and not paragraph_style(el).startswith("TOC"),
        )
        idx_ack = find_first_index_from(
            elements,
            (idx_refs + 1) if idx_refs is not None else 0,
            lambda el: is_paragraph(el)
            and is_heading_text(paragraph_text(el), "ack")
            and not paragraph_style(el).startswith("TOC"),
        )
        sect_pr = body.find("w:sectPr", NS)

        needed = {
            "模板摘要": idx_cn,
            "模板目录": idx_toc,
            "模板第1章": idx_ch1,
            "模板参考文献": idx_refs,
            "模板致谢": idx_ack,
        }
        missing = [name for name, idx in needed.items() if idx is None]
        if sect_pr is None:
            missing.append("模板节属性")
        if missing:
            raise ValueError(f"Template document missing markers: {', '.join(missing)}")

        main_elements = elements[idx_ch1:] if idx_ch1 is not None else elements
        refs_elements = elements[idx_refs:] if idx_refs is not None else elements
        ack_elements = elements[idx_ack:] if idx_ack is not None else elements

        samples = {
            "abstract_heading": sample_from_template(elements, "abstract_heading"),
            "en_abstract_heading": sample_from_template(elements, "en_abstract_heading"),
            "chapter": sample_from_template(main_elements, "chapter"),
            "heading2": sample_from_template(main_elements, "heading2"),
            "heading3": sample_from_template(main_elements, "heading3"),
            "heading4": sample_from_template(main_elements, "heading4"),
            "refs_heading": sample_from_template(refs_elements, "refs_heading"),
            "ack_heading": sample_from_template(ack_elements, "ack_heading"),
            "body": sample_from_template(main_elements, "body"),
        }

        toc_elements = [copy.deepcopy(el) for el in elements[idx_toc:idx_ch1]]
        before_cover = [copy.deepcopy(el) for el in elements[:idx_cn]]
        abstract_to_toc_break = copy.deepcopy(elements[idx_toc - 1]) if idx_toc and is_paragraph(elements[idx_toc - 1]) else None
        toc_to_main_break = copy.deepcopy(elements[idx_ch1 - 1]) if idx_ch1 and is_paragraph(elements[idx_ch1 - 1]) else None

        cn_abstract_rendered = build_rendered_paragraphs(ranges["cn_abstract"], samples)

        en_abstract_texts = ranges["en_abstract"]
        en_abstract_rendered = []
        if en_abstract_texts:
            normal_part = en_abstract_texts[:-1]
            en_abstract_rendered.extend(build_rendered_paragraphs(normal_part, samples))
            if abstract_to_toc_break is not None:
                en_break_para = paragraph_with_text(en_abstract_texts[-1], abstract_to_toc_break)
                en_abstract_rendered.append(en_break_para)
            else:
                en_abstract_rendered.extend(build_rendered_paragraphs([en_abstract_texts[-1]], samples))

        main_rendered = build_rendered_paragraphs(ranges["main"], samples)
        refs_rendered = build_rendered_paragraphs(ranges["refs"], samples)
        ack_rendered = build_rendered_paragraphs(ranges["ack"], samples)

        new_body_children = []
        new_body_children.extend(before_cover)
        new_body_children.extend(cn_abstract_rendered)
        new_body_children.extend(en_abstract_rendered)
        new_body_children.extend(toc_elements)
        new_body_children.extend(main_rendered)
        new_body_children.extend(refs_rendered)
        new_body_children.extend(ack_rendered)
        new_body_children.append(copy.deepcopy(sect_pr))

        body.clear()
        for child in new_body_children:
            body.append(child)

        ET.register_namespace("w", W_NS)
        ET.register_namespace("wpc", "http://schemas.microsoft.com/office/word/2010/wordprocessingCanvas")
        ET.register_namespace("mc", "http://schemas.openxmlformats.org/markup-compatibility/2006")
        ET.register_namespace("o", "urn:schemas-microsoft-com:office:office")
        ET.register_namespace("r", "http://schemas.openxmlformats.org/officeDocument/2006/relationships")
        ET.register_namespace("m", "http://schemas.openxmlformats.org/officeDocument/2006/math")
        ET.register_namespace("v", "urn:schemas-microsoft-com:vml")
        ET.register_namespace("wp14", "http://schemas.microsoft.com/office/word/2010/wordprocessingDrawing")
        ET.register_namespace("wp", "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing")
        ET.register_namespace("w10", "urn:schemas-microsoft-com:office:word")
        ET.register_namespace("w14", "http://schemas.microsoft.com/office/word/2010/wordml")
        ET.register_namespace("w15", "http://schemas.microsoft.com/office/word/2012/wordml")
        ET.register_namespace("w16cex", "http://schemas.microsoft.com/office/word/2018/wordml/cex")
        ET.register_namespace("w16cid", "http://schemas.microsoft.com/office/word/2016/wordml/cid")
        ET.register_namespace("w16", "http://schemas.microsoft.com/office/word/2018/wordml")
        ET.register_namespace("w16du", "http://schemas.microsoft.com/office/word/2023/wordml/word16du")
        ET.register_namespace("w16sdtdh", "http://schemas.microsoft.com/office/word/2020/wordml/sdtdatahash")
        ET.register_namespace("w16sdtfl", "http://schemas.microsoft.com/office/word/2024/wordml/sdtformatlock")
        ET.register_namespace("w16se", "http://schemas.microsoft.com/office/word/2015/wordml/symex")
        ET.register_namespace("wpg", "http://schemas.microsoft.com/office/word/2010/wordprocessingGroup")
        ET.register_namespace("wpi", "http://schemas.microsoft.com/office/word/2010/wordprocessingInk")
        ET.register_namespace("wne", "http://schemas.microsoft.com/office/word/2006/wordml")
        ET.register_namespace("wps", "http://schemas.microsoft.com/office/word/2010/wordprocessingShape")

        ET.ElementTree(root).write(document_xml, encoding="utf-8", xml_declaration=True)

        with zipfile.ZipFile(output_doc, "w", zipfile.ZIP_DEFLATED) as zf:
            for path in tmpdir.rglob("*"):
                if path.is_file():
                    zf.write(path, path.relative_to(tmpdir).as_posix())


def main():
    parser = argparse.ArgumentParser(description="Fill a DOCX template with thesis content.")
    parser.add_argument(
        "--template",
        default=r"C:\Users\ASUS\Downloads\叶子龙-毕设论文.docx",
        help="Path to the reference template DOCX",
    )
    parser.add_argument(
        "--source",
        default=r"C:\Users\ASUS\Downloads\新建 DOCX 文档.docx",
        help="Path to the source content DOCX",
    )
    parser.add_argument(
        "--output",
        default=r"C:\Users\ASUS\Downloads\毕业论文_自动套模板.docx",
        help="Path to write the generated DOCX",
    )
    args = parser.parse_args()

    template_doc = Path(args.template)
    source_doc = Path(args.source)
    output_doc = Path(args.output)

    if not template_doc.exists():
        raise FileNotFoundError(f"Template not found: {template_doc}")
    if not source_doc.exists():
        raise FileNotFoundError(f"Source not found: {source_doc}")

    output_doc.parent.mkdir(parents=True, exist_ok=True)
    update_document_xml(template_doc, source_doc, output_doc)
    print(f"Done: {output_doc}")


if __name__ == "__main__":
    main()
