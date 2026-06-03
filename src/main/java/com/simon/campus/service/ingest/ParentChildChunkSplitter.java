package com.simon.campus.service.ingest;

import com.simon.campus.model.entity.ChildChunk;
import com.simon.campus.model.entity.ParentChunk;
import com.simon.campus.service.ingest.DocumentParser.ParsedSection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ParentChildChunkSplitter {

    // Rough char counts (Chinese avg ~1.5 chars/token, English ~4 chars/token)
    private static final int PARENT_MIN_CHARS = 400;
    private static final int PARENT_MAX_CHARS = 800;
    private static final int CHILD_MIN_CHARS  = 80;
    private static final int CHILD_MAX_CHARS  = 160;
    private static final int CHILD_OVERLAP    = 25;

    public record SplitResult(List<ParentChunk> parents, List<ChildChunk> children) {}

    public SplitResult split(String docId, String docTitle, int accessLevel,
                             List<ParsedSection> sections) {
        List<ParentChunk> parents  = new ArrayList<>();
        List<ChildChunk>  children = new ArrayList<>();

        for (ParsedSection section : sections) {
            List<String> parentTexts = splitToParents(section.content());
            for (String parentText : parentTexts) {
                String parentId = UUID.randomUUID().toString().replace("-", "");
                String headingPath = section.heading();

                ParentChunk parent = ParentChunk.builder()
                    .parentId(parentId)
                    .docId(docId)
                    .docTitle(docTitle)
                    .headingPath(headingPath)
                    .content(parentText)
                    .pageStart(section.pageStart())
                    .pageEnd(section.pageStart())
                    .accessLevel(accessLevel)
                    .build();
                parents.add(parent);

                // Split parent into children
                List<String> childTexts = splitToChildren(parentText);
                int offset = 0;
                for (int i = 0; i < childTexts.size(); i++) {
                    String childText = childTexts.get(i);
                    String childId = parentId + "_" + i;
                    int startOffset = findOffset(parentText, childText, offset);
                    int endOffset = startOffset + childText.length();

                    ChildChunk child = ChildChunk.builder()
                        .childId(childId)
                        .parentId(parentId)
                        .docId(docId)
                        .docTitle(docTitle)
                        .headingPath(headingPath)
                        .content(childText)
                        .pageStart(section.pageStart())
                        .startOffset(startOffset)
                        .endOffset(endOffset)
                        .accessLevel(accessLevel)
                        .build();
                    children.add(child);
                    offset = Math.max(0, endOffset - CHILD_OVERLAP);
                }
            }
        }

        log.info("Split doc {} into {} parents, {} children", docId, parents.size(), children.size());
        return new SplitResult(parents, children);
    }

    private List<String> splitToParents(String text) {
        return splitByLength(text, PARENT_MIN_CHARS, PARENT_MAX_CHARS, 0);
    }

    private List<String> splitToChildren(String text) {
        return splitByLength(text, CHILD_MIN_CHARS, CHILD_MAX_CHARS, CHILD_OVERLAP);
    }

    private List<String> splitByLength(String text, int minChars, int maxChars, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        // Split on sentence boundaries first
        String[] sentences = text.split("(?<=[。！？.!?\\n])");
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence.isBlank()) continue;
            if (current.length() + sentence.length() > maxChars && current.length() >= minChars) {
                result.add(current.toString().strip());
                // Overlap: keep last `overlap` chars
                String tail = current.toString();
                current = new StringBuilder(tail.length() > overlap ? tail.substring(tail.length() - overlap) : tail);
            }
            current.append(sentence);
        }

        String remaining = current.toString().strip();
        if (!remaining.isEmpty()) {
            // Merge tiny tails into previous chunk instead of creating a stub
            if (!result.isEmpty() && remaining.length() < minChars / 2) {
                String last = result.remove(result.size() - 1);
                result.add((last + remaining).strip());
            } else {
                result.add(remaining);
            }
        }

        if (result.isEmpty() && !text.isBlank()) {
            result.add(text.strip());
        }
        return result;
    }

    private int findOffset(String parent, String child, int startFrom) {
        int idx = parent.indexOf(child, startFrom);
        return idx >= 0 ? idx : 0;
    }
}
