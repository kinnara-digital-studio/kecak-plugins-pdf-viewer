<style type="text/css">
.pnx-icon-pdf-viewer {
    width: 24px !important;
    height: 24px !important;
}
</style>

<div class="form-cell" ${elementMetaData!}>
    <label class="label">${menu.properties.label}</label>
    <br>
    <embed src="${menu.properties.pdfUrl}" width="${menu.properties.width!320}" height="${menu.properties.height!320}" type="application/pdf">
</div>