"ee-component" {
    system = "CLASSIC"
    componentDisplayName = "EE Component"
    componentOwner = "EE Component Owner"
    releaseManager = "EE Component Release Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/ee-component.git"
    solution = true
    jira {
        projectKey = 'EE'
        lineVersionFormat = '$major02.$minor02'
        majorVersionFormat = '$major02.$minorC.$serviceC'
        releaseVersionFormat = '$major02.$minor02.$service02.$fix02'
        buildVersionFormat = '$major02.$minor02.$service02.$fix02-$build'
        displayName = 'EE Component'
    }
    distribution {
        external = true
        explicit = true
        docker = "test/test-component"
    }
}

"ee-component-with-version-ranges" {
    system = "CLASSIC"
    componentDisplayName = "EE Component With Version Ranges"
    componentOwner = "EE Component Owner"
    releaseManager = "EE Component Release Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/ee-component-with-version-ranges.git"
    solution = true
    jira {
        projectKey = 'EEVR'
        lineVersionFormat = '$major02.$minor02'
        majorVersionFormat = '$major02.$minorC.$serviceC'
        releaseVersionFormat = '$major02.$minor02.$service02.$fix02'
        buildVersionFormat = '$major02.$minor02.$service02.$fix02-$build'
        displayName = 'EE Component With Version Ranges'
    }
    distribution {
        external = true
        explicit = false
        docker = "test/test-component-vr"
    }
    "[03.53,03.54)" {
        distribution {
            docker = "test/test-component-vr"
        }
    }
    "[03.54,03.55)" {
        distribution {
            docker = "test/test-component-vr"
        }
    }
    "[03.55,03.56)" {
        distribution {
            docker = "test/test-component-vr"
        }
    }
}

"ee-client-specific-component" {
    system = "CLASSIC"
    componentDisplayName = "EE Client Specific Component"
    componentOwner = "EE Component Owner"
    releaseManager = "EE Component Release Manager"
    clientCode = "CLIENT_CODE"
    parentComponent = "ee-component"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/ee-client-specific-component.git"
    jira {
        projectKey = 'EEC'
        lineVersionFormat = '$major02.$minor02'
        majorVersionFormat = '$major02.$minorC.$serviceC'
        releaseVersionFormat = '$major02.$minor02.$service02.$fix02'
        buildVersionFormat = '$major02.$minor02.$service02.$fix02-$build'
        displayName = 'EEC Component'
    }
    distribution {
        external = true
        explicit = true
    }
}

"ie-component" {
    componentDisplayName = "IE Component"
    componentOwner = "IE Component Owner"
    releaseManager = "IE Component Release Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ie/ie-component.git"
    jira {
        projectKey = 'IE'
        displayName = 'IE Component'
    }
}

"ei-component" {
    componentDisplayName = "EI Component"
    componentOwner = "EI Component Owner"
    releaseManager = "EI Component Release Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ei/ei-component.git"
    jira {
        projectKey = 'EI'
        displayName = 'EI Component'
    }
    distribution {
        explicit = true
        external = false
        GAV = 'corp.domain:ei-component:zip'
    }
}

"ii-component" {
    componentDisplayName = "II Component"
    componentOwner = "II Component Owner"
    releaseManager = "II Component Release Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ii/ii-component.git"
    jira {
        projectKey = 'II'
        displayName = 'II Component'
    }
    distribution {
        explicit = false
        external = false
    }
}

"archived-component" {
    componentDisplayName = "Archived Component"
    componentOwner = "Archived Component Owner"
    releaseManager = "Archived Component Release Manager"
    archived = true
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ii/ii-component.git"
    jira {
        projectKey = 'ARCHIVED'
        displayName = 'Archived Component'
    }
    distribution {
        explicit = false
        external = true
    }
}

dependency1 {
    system = "CLASSIC"
    componentDisplayName = "Dependency 1"
    componentOwner = "Dependency Owner"
    releaseManager = "Dependency Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/dependency-1.git"
    jira {
        projectKey = 'DEPS'
    }
    distribution {
        external = true
        explicit = true
    }
}

dependency2 {
    system = "CLASSIC"
    componentDisplayName = "Dependency 2"
    componentOwner = "Dependency Owner"
    releaseManager = "Dependency Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/dependency-2.git"
    jira {
        projectKey = 'DEPS'
        component {
            versionPrefix = 'dep2'
        }
    }
    distribution {
        external = true
        explicit = true
    }
}

dependency3 {
    system = "CLASSIC"
    componentDisplayName = "Dependency 3"
    componentOwner = "Dependency Owner"
    releaseManager = "Dependency Manager"
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ee/dependency-3.git"
    jira {
        projectKey = 'DEPS'
        component {
            versionPrefix = 'dep3'
        }
    }
    distribution {
        external = true
        explicit = true
    }
}

"archived-dependency" {
    componentDisplayName = "Archived Dependency"
    componentOwner = "Archived Dependency Owner"
    releaseManager = "Archived Dependency Release Manager"
    archived = true
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/ad/archived-dependency.git"
    jira {
        projectKey = 'ARCHDEP'
        displayName = 'Archived Dependency'
    }
    distribution {
        explicit = false
        external = true
    }
}

"solution-with-archived-dependency" {
    system = "CLASSIC"
    componentDisplayName = "Solution With Archived Dependency"
    componentOwner = "Solution With Archived Dependency Owner"
    releaseManager = "Solution With Archived Dependency Release Manager"
    solution = true
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/swad/solution-with-archived-dependency.git"
    jira {
        projectKey = 'SWAD'
        displayName = 'Solution With Archived Dependency'
    }
    distribution {
        explicit = true
        external = true
    }
}

"solution-parent" {
    system = "CLASSIC"
    componentDisplayName = "Solution Parent"
    componentOwner = "Solution Parent Owner"
    releaseManager = "Solution Parent Release Manager"
    solution = true
    groupId = "corp.domain"
    vcsUrl = "ssh://git@git.domain.corp/sp/solution-parent.git"
    jira {
        projectKey = 'SPARENT'
        displayName = 'Solution Parent'
    }
    distribution {
        explicit = true
        external = true
    }
}
