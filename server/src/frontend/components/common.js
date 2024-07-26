import get from "lodash/get"
import {Icon, Spinner, Tooltip} from "@blueprintjs/core";
import React from "react";

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
    return htmlRegex.test(fileName)
}

export function getSecondaryLabel(object) {
    const {loading, loadError, errorMessage} = object
    let secondaryLabel
    if (loadError) {
        secondaryLabel = <Tooltip
            content={errorMessage}
            position='top-right'
        >
            <div className='error-icon-components-tree-wrap'>
                <Icon icon='error' size={16} intent="danger"/>
            </div>
        </Tooltip>
    }
    if (loading) {
        secondaryLabel = <Spinner size={16} intent="primary"/>
    }
    return secondaryLabel
}