import get from "lodash/get"

const searchQueryRegex = /^([\w-]{3,}) (\d+(\.\d+)*(-\d+)?)$/
const htmlRegex = new RegExp('^.+\\.html?$', 'i')

export function checkQuery(query) {
    return query.match(searchQueryRegex) != null
}

export function hasPermission(loggedUser, permission) {
    return !!permission && get(loggedUser, 'roles', [])
        .flatMap(r => r.permissions)
        .includes(permission)
}

export const printableArtifactTypes = ["report", "manuals", "notes", "static"]

export function isPrintableArtifact(artifact) {
    return !!artifact && printableArtifactTypes.includes(artifact.type)
}

export function isHtml(fileName) {
    console.debug('isHtml', fileName, htmlRegex.test(fileName))
    return htmlRegex.test(fileName)
}
