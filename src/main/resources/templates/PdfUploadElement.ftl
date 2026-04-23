<style>
    #pdf-preview-wrap-${elementParamName!} {
        margin-top: 10px;
        display: none;
    }
    #pdf-preview-wrap-${elementParamName!} iframe {
        width: 100%;
        height: 400px;
        border: 1px solid #dee2e6;
        border-radius: 4px;
    }
    #drop-zone-${elementParamName!} {
        border: 2px dashed #007bff;
        border-radius: 5px;
        padding: 20px;
        text-align: center;
        background: #f8f9fa;
        cursor: pointer;
        transition: background 0.2s;
    }
    #drop-zone-${elementParamName!}:hover,
    #drop-zone-${elementParamName!}.drag-over {
        background: #e3f0ff;
    }
    .pdf-file-item {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-top: 8px;
        padding: 8px;
        background: #f8f9fa;
        border-radius: 4px;
    }
    .pdf-file-item a {
        flex: 1;
    }
    .pdf-preview-toggle {
        font-size: smaller;
        color: #007bff;
        cursor: pointer;
        text-decoration: underline;
        white-space: nowrap;
    }
    .pdf-inline-preview {
        width: 100%;
        height: 400px;
        border: 1px solid #dee2e6;
        border-radius: 4px;
        margin-top: 6px;
        display: none;
    }
</style>

<div class="form-cell" ${elementMetaData!}>
    <label class="label">
        ${element.properties.label!}
        <span class="form-cell-validator">${decoration!}</span>
        <#if error??>
            <span class="form-error-message">${error}</span>
        </#if>
    </label>

    <div class="form-fileupload">

        <#if element.properties.readonly! != 'true'>
            <div id="drop-zone-${elementParamName!}">
                <p style="margin:0 0 4px; color:#555;">
                    Drag & Drop PDF atau <strong>klik untuk pilih</strong>
                </p>
                <small style="color:#999;">Hanya file PDF</small>
                <input id="input-${elementParamName!}"
                       name="${elementParamName!}"
                       type="file"
                       accept="application/pdf"
                       <#if element.properties.multiple! == 'true'>multiple</#if>
                       <#if error??>class="form-error-cell"</#if>
                       style="display:none;"/>
            </div>

            <!-- Preview inline untuk file baru (sebelum submit) -->
            <div id="pdf-preview-wrap-${elementParamName!}">
                <iframe id="pdf-preview-${elementParamName!}"></iframe>
            </div>
        </#if>

        <!-- List file yang sudah ada -->
        <#if tempFilePaths?? || filePaths??>
            <ul class="form-fileupload-value" style="list-style:none; padding:0; margin-top:8px;">

                <#if tempFilePaths??>
                    <#list tempFilePaths?keys as key>
                        <li>
                            <div class="pdf-file-item">
                                <span>📄 ${tempFilePaths[key]!?html}</span>
                                <input type="hidden" name="${elementParamName!}_path" value="${key!?html}"/>
                                <#if element.properties.readonly! != 'true'>
                                    <input type="checkbox" name="${elementParamName!}_remove" value="${key!?html}"/>
                                    <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                                </#if>
                            </div>
                        </li>
                    </#list>
                </#if>

                <#if filePaths??>
                    <#list filePaths?keys as key>
                        <li>
                            <div class="pdf-file-item">
                                <a href="${request.contextPath}${key!?html}" target="_blank">
                                    📄 ${filePaths[key]!?html}
                                </a>
                                <span class="pdf-preview-toggle"
                                      data-url="${request.contextPath}${key!?html}"
                                      data-id="${elementParamName!}-${key?index}">
                                    ▶ Preview
                                </span>
                                <input type="hidden" name="${elementParamName!}_path" value="${filePaths[key]!?html}"/>
                                <#if element.properties.readonly! != 'true'>
                                    <input type="checkbox" name="${elementParamName!}_remove" value="${filePaths[key]!?html}"/>
                                    <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                                </#if>
                            </div>
                            <!-- Preview inline per file -->
                            <iframe id="preview-${elementParamName!}-${key?index}"
                                    class="pdf-inline-preview"></iframe>
                        </li>
                    </#list>
                </#if>

            </ul>
        </#if>

    </div>
</div>

<script type="text/javascript">
(function() {
    const paramName = "${elementParamName!}";
    const dropZone  = document.getElementById('drop-zone-' + paramName);
    const fileInput = document.getElementById('input-'     + paramName);
    const previewWrap = document.getElementById('pdf-preview-wrap-' + paramName);
    const previewFrame = document.getElementById('pdf-preview-'     + paramName);

    // ── Drag & Drop ──────────────────────────────────────────────
    if (dropZone) {
        dropZone.addEventListener('click', () => fileInput.click());

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            dropZone.classList.add('drag-over');
        });
        dropZone.addEventListener('dragleave', () => {
            dropZone.classList.remove('drag-over');
        });
        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            dropZone.classList.remove('drag-over');
            if (e.dataTransfer.files.length > 0) handleFile(e.dataTransfer.files[0]);
        });

        fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) handleFile(e.target.files[0]);
        });
    }

    function handleFile(file) {
        if (file.type !== 'application/pdf') {
            alert('Hanya file PDF yang diperbolehkan.');
            return;
        }

        <#if element.properties.maxSize?? && element.properties.maxSize != ''>
        const maxMB = parseFloat('${element.properties.maxSize!}');
        if (file.size / 1024 / 1024 > maxMB) {
            alert('${element.properties.maxSizeMsg!'File terlalu besar'} (Maks: ' + maxMB + ' MB)');
            fileInput.value = '';
            return;
        }
        </#if>

        // ✅ Preview langsung dari browser — tidak perlu upload ke server
        const url = URL.createObjectURL(file);
        previewFrame.src = url;
        previewWrap.style.display = 'block';

        // Update label drop zone
        dropZone.querySelector('p').textContent = '📄 ' + file.name;
    }

    // ── Toggle preview untuk file tersimpan ──────────────────────
    document.querySelectorAll('.pdf-preview-toggle').forEach(function(btn) {
        btn.addEventListener('click', function() {
            const url   = this.getAttribute('data-url');
            const id    = this.getAttribute('data-id');
            const frame = document.getElementById('preview-' + id);

            if (!frame) return;

            if (frame.style.display === 'block') {
                // Sembunyikan
                frame.style.display = 'none';
                frame.src = '';
                this.textContent = '▶ Preview';
            } else {
                // Tampilkan
                frame.src = url;
                frame.style.display = 'block';
                this.textContent = '▼ Tutup';
            }
        });
    });

})();
</script>