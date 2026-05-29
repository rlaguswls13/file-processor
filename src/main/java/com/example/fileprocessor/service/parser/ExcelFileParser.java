package com.example.fileprocessor.service.parser;

import com.example.fileprocessor.model.AddressBook;
import com.example.fileprocessor.model.TargetData;
import com.example.fileprocessor.repository.BatchInsertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExcelFileParser implements FileParser {

    private final BatchInsertRepository batchInsertRepository;
    private static final int CHUNK_SIZE = 1000;

    @Override
    public void parseAndSave(File file, String taskType) throws Exception {
        log.info("Starting high-performance Excel (XLSX) streaming parse: {} for task: {}", file.getName(), taskType);

        // XLS (옛날 바이너리 엑셀)은 대용량 처리에 제한이 있어 기본 메모리 파싱을 하거나, XLSX 전용 고성능 파서를 제공함.
        if (file.getName().endsWith(".xls")) {
            log.warn("Legacy XLS format detected. Processing with memory-bound parser. XLSX is highly recommended for large datasets.");
            // 뼈대이므로 본 기능은 XLSX 기반 고성능 SAX 스트리밍을 핵심 모델로 탑재하고, XLS는 주석 등으로 뼈대 지원
            // 실제 구현에서는 XLSX 대용량 스트리밍 아키텍처를 위주로 완비합니다.
        }

        // 1. XLSX 파일을 Zip 컨테이너 형태로 스트리밍 오픈 (디스크 캐싱)
        try (OPCPackage opcPackage = OPCPackage.open(file, PackageAccess.READ)) {
            ReadOnlySharedStringsTable stringsTable = new ReadOnlySharedStringsTable(opcPackage);
            XSSFReader xssfReader = new XSSFReader(opcPackage);
            StylesTable stylesTable = xssfReader.getStylesTable();
            
            // 첫 번째 시트 조회
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) xssfReader.getSheetsData();
            if (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    XMLReader sheetParser = XMLReaderFactory.createXMLReader();
                    
                    // 대용량 처리를 위한 스트리밍 Sheet Contents Handler 바인딩
                    ExcelSheetHandler handler = new ExcelSheetHandler(batchInsertRepository, taskType);
                    ContentHandler contentHandler = new XSSFSheetXMLHandler(stylesTable, stringsTable, handler, false);
                    sheetParser.setContentHandler(contentHandler);
                    
                    // 파싱 수행 (SAX 이벤트 기반으로 돌기 때문에 메모리 점유가 극도로 낮음)
                    sheetParser.parse(new InputSource(sheetStream));
                    
                    // 마지막 배치 플러시
                    handler.flush();
                }
            }
        }
        log.info("Finished Excel streaming parse for file: {}", file.getName());
    }

    /**
     * Excel 시트의 행(Row) 이벤트를 수신하여 처리하는 고성능 SAX 핸들러 클래스
     */
    private static class ExcelSheetHandler implements SheetContentsHandler {
        private final BatchInsertRepository repository;
        private final String taskType;
        
        // 현재 행의 셀 데이터를 임시 수집 (열 번호 -> 값)
        private final Map<Integer, String> currentRowData = new HashMap<>();
        private final Map<String, Integer> headerMap = new HashMap<>();
        
        private final List<AddressBook> addressBookChunk = new ArrayList<>(CHUNK_SIZE);
        private final List<TargetData> targetDataChunk = new ArrayList<>(CHUNK_SIZE);
        
        private int currentRowNum = 0;
        private boolean isHeaderRow = true;

        public ExcelSheetHandler(BatchInsertRepository repository, String taskType) {
            this.repository = repository;
            this.taskType = taskType;
        }

        @Override
        public void startRow(int rowNum) {
            currentRowNum = rowNum;
            currentRowData.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (currentRowNum == 0 && isHeaderRow) {
                // 헤더 파싱 및 매핑 구성
                for (Map.Entry<Integer, String> entry : currentRowData.entrySet()) {
                    headerMap.put(entry.getValue().toLowerCase().trim(), entry.getKey());
                }
                isHeaderRow = false;
                return;
            }

            // 실 데이터 처리
            try {
                if ("ADDRESS_BOOK".equalsIgnoreCase(taskType)) {
                    processAddressBookRow();
                } else if ("TARGET_DATA".equalsIgnoreCase(taskType)) {
                    processTargetDataRow();
                }
            } catch (Exception e) {
                log.error("Error processing excel row {}: {}", rowNum, e.getMessage());
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            // 셀의 주소(예: A1, B3)에서 열 인덱스 추출
            int colIdx = getColumnIndex(cellReference);
            currentRowData.put(colIdx, formattedValue);
        }

        private void processAddressBookRow() {
            String name = getCellValue("name");
            String phoneNumber = getCellValue("phone_number");
            String email = getCellValue("email");
            String groupName = getCellValue("group_name");

            if (name == null && phoneNumber == null) return; // 빈 로우 무시

            AddressBook ab = AddressBook.builder()
                    .name(name != null ? name : "")
                    .phoneNumber(phoneNumber != null ? phoneNumber : "")
                    .email(email != null ? email : "")
                    .groupName(groupName != null ? groupName : "")
                    .createdAt(LocalDateTime.now())
                    .build();

            addressBookChunk.add(ab);

            if (addressBookChunk.size() >= CHUNK_SIZE) {
                repository.saveAddressBooksInBatch(addressBookChunk);
                addressBookChunk.clear();
            }
        }

        private void processTargetDataRow() {
            String userId = getCellValue("user_id");
            String actionPattern = getCellValue("action_pattern");
            String targetGroup = getCellValue("target_group");
            String scoreStr = getCellValue("score");

            if (userId == null) return;

            Double score = 0.0;
            if (scoreStr != null) {
                try {
                    score = Double.parseDouble(scoreStr);
                } catch (NumberFormatException ignored) {}
            }

            TargetData td = TargetData.builder()
                    .userId(userId)
                    .actionPattern(actionPattern != null ? actionPattern : "")
                    .targetGroup(targetGroup != null ? targetGroup : "")
                    .score(score)
                    .createdAt(LocalDateTime.now())
                    .build();

            targetDataChunk.add(td);

            if (targetDataChunk.size() >= CHUNK_SIZE) {
                repository.saveTargetDataInBatch(targetDataChunk);
                targetDataChunk.clear();
            }
        }

        private String getCellValue(String headerName) {
            Integer colIdx = headerMap.get(headerName);
            if (colIdx == null) return null;
            return currentRowData.get(colIdx);
        }

        private int getColumnIndex(String cellReference) {
            // "A1" -> 0, "B1" -> 1, "AA1" -> 26 등 추출 로직
            String colName = cellReference.replaceAll("[0-9]", "");
            int idx = 0;
            for (int i = 0; i < colName.length(); i++) {
                idx *= 26;
                idx += (colName.charAt(i) - 'A' + 1);
            }
            return idx - 1;
        }

        public void flush() {
            if (!addressBookChunk.isEmpty()) {
                repository.saveAddressBooksInBatch(addressBookChunk);
                addressBookChunk.clear();
            }
            if (!targetDataChunk.isEmpty()) {
                repository.saveTargetDataInBatch(targetDataChunk);
                targetDataChunk.clear();
            }
        }
    }
}
